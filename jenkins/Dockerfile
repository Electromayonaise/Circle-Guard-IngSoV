FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update -qq && \
    apt-get install -y -qq \
        curl ca-certificates apt-transport-https lsb-release gnupg \
        git python3 python3-venv python3-pip && \
    # Azure CLI
    curl -sL https://aka.ms/InstallAzureCLIDeb | bash && \
    # Docker CLI
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker.gpg && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list && \
    apt-get update -qq && \
    apt-get install -y -qq docker-ce-cli && \
    # kubectl
    curl -LO "https://dl.k8s.io/release/v1.31.0/bin/linux/amd64/kubectl" && \
    install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl && \
    rm kubectl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

USER jenkins
