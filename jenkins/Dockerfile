FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update -qq && \
    apt-get install -y -qq curl ca-certificates apt-transport-https lsb-release gnupg && \
    curl -sL https://aka.ms/InstallAzureCLIDeb | bash && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

USER jenkins
