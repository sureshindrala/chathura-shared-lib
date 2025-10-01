package com.chathura.builds;
class Docker {
// write all the methods here docker //back to normal
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }

    // Application Build
    def buildApp(appName) {
        jenkins.sh """
            echo "Building the $appName Application"
            mvn clean package -DskipTests=true
        """
    }
}