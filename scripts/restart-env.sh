#!/usr/bin/env bash
# Run this after Docker Desktop restarts to re-wire IPs and network connections.
set -e

echo "=== CircleGuard environment recovery ==="

# 1. Get current Kind registry IP
REGISTRY_IP=$(docker inspect circleguard-registry --format '{{.NetworkSettings.Networks.kind.IPAddress}}' 2>/dev/null)
if [ -z "$REGISTRY_IP" ]; then
  echo "ERROR: circleguard-registry container not found or not running"
  exit 1
fi
echo "Registry IP: $REGISTRY_IP"

# 2. Update containerd mirror config on all Kind nodes
# Use docker directly to avoid dependency on kind CLI being in PATH
KIND_NODES=$(docker ps --format '{{.Names}}' | grep '^circleguard-' | grep -v 'registry')
if [ -z "$KIND_NODES" ]; then
  echo "ERROR: no Kind nodes found (circleguard-* containers)"
  exit 1
fi
for NODE in $KIND_NODES; do
  echo "Updating mirror on $NODE..."
  docker exec "$NODE" sh -c "
    mkdir -p /etc/containerd/certs.d/localhost:5000
    cat > /etc/containerd/certs.d/localhost:5000/hosts.toml <<EOF
server = \"http://localhost:5000\"
[host.\"http://${REGISTRY_IP}:5000\"]
  capabilities = [\"pull\", \"resolve\", \"push\"]
  skip_verify = true
EOF
    systemctl restart containerd
  "
done
echo "Containerd mirrors updated"

# 3. Fix Docker socket permissions (reset by Docker Desktop on restart)
docker exec -u root jenkins sh -c "chmod 666 /var/run/docker.sock"
echo "Docker socket permissions fixed"

# 4. Reconnect Jenkins to kind network (safe to run even if already connected)
docker network connect kind jenkins 2>/dev/null && echo "Jenkins connected to kind network" || echo "Jenkins already on kind network"

# 5. Get current control-plane IP
CP_IP=$(docker inspect circleguard-control-plane --format '{{.NetworkSettings.Networks.kind.IPAddress}}' 2>/dev/null)
if [ -z "$CP_IP" ]; then
  echo "ERROR: could not get control-plane IP"
  exit 1
fi
echo "Control-plane IP: $CP_IP"

# 6. Update kubeconfig inside Jenkins to use hostname (stable across IP changes)
docker exec jenkins sh -c "
  sed -i 's|server: https://[0-9.]*:6443|server: https://circleguard-control-plane:6443|g' /var/jenkins_home/.kube/config
  sed -i 's|certificate-authority-data:.*|insecure-skip-tls-verify: true|g' /var/jenkins_home/.kube/config
"
echo "Jenkins kubeconfig updated -> https://circleguard-control-plane:6443"

# 7. Verify
docker exec jenkins sh -c "kubectl get nodes" && echo "=== kubectl OK ===" || echo "WARNING: kubectl check failed"

echo "=== Done. Jenkins available at http://localhost:8090 ==="
echo "    Admin password: $(docker exec jenkins sh -c 'cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo unknown')"
