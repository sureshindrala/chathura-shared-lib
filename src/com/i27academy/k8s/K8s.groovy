package com.i27academy.builds;
class K8s {
// write all the methods here 
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }

    // Method to authenticate to kubernetes clusters
    def auth_login(clusterName, zone, projectID){
        jenkins.sh """
            echo "********************* Entering into kubernetes Authentication/login Method *********************"
            gcloud compute instances list
            echo "********************* Create the config file for the environment *********************"
            gcloud container clusters get-credentials ${clusterName} --zone ${zone} --project ${projectID}
            kubectl get nodes 
        """
    }
}

