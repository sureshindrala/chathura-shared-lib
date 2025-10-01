package com.chathura.builds

class Docker {
    def jenkins

    Docker(jenkins) {
        this.jenkins = jenkins
    }

    // Build Java/Maven application
    def buildApp(String appName) {
        jenkins.sh """
            echo "Building ${appName} Application"
            mvn clean package -DskipTests=true
        """
    }

    // Docker build and push
    def buildAndPushDocker(String appName, String version, String dockerHub, String gitCommit, creds) {
        jenkins.sh """
            echo "Copying JAR for Docker build..."
            cp ${jenkins.env.WORKSPACE}/target/chathura-${appName}-${version}.${jenkins.env.POM_PACKAGING} ./.cicd
            ls -la ./.cicd
            echo "Building Docker image..."
            docker build --force-rm --no-cache --pull --rm=true \
                --build-arg JAR_SOURCE=chathura-${appName}-${version}.${jenkins.env.POM_PACKAGING} \
                -t ${dockerHub}/${appName}:${gitCommit} ./.cicd
            echo "Logging into Docker Hub..."
            docker login -u ${creds_USR} -p ${creds_PSW}
            docker push ${dockerHub}/${appName}:${gitCommit}
        """
    }

    // Docker image validation: pull if exists, else build & push
    def validateImage(String appName, String version, String dockerHub, String gitCommit, creds) {
        jenkins.echo "Validating Docker image..."
        try {
            jenkins.sh "docker pull ${dockerHub}/${appName}:${gitCommit}"
            jenkins.echo "Docker image pulled successfully!"
        } catch (Exception e) {
            jenkins.echo "Image not found. Building and pushing..."
            buildApp(appName)
            buildAndPushDocker(appName, version, dockerHub, gitCommit, creds)
        }
    }

    // Deploy container to remote host
    def deploy(String envDeploy, String hostPort, String contPort, String appName, String dockerHub, String gitCommit, creds, String dockerServer) {
        jenkins.withCredentials([jenkins.usernamePassword(credentialsId: creds,
                                                           passwordVariable: 'PASSWORD',
                                                           usernameVariable: 'USERNAME')]) {
            try {
                // Stop and remove old container
                jenkins.sh """
                    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${dockerServer}" \
                        "docker stop ${appName}-${envDeploy} || true"
                    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${dockerServer}" \
                        "docker rm ${appName}-${envDeploy} || true"
                """
                // Run new container
                jenkins.sh """
                    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${dockerServer}" \
                        "docker run -dit -p ${hostPort}:${contPort} --name ${appName}-${envDeploy} ${dockerHub}/${appName}:${gitCommit}"
                    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${dockerServer}" "docker ps"
                """
            } catch (err) {
                jenkins.echo "Error during deployment: ${err}"
            }
        }
    }
}
