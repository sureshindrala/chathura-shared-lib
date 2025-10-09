package com.chathura.k8s;
class K8s {
// write all the methods here 
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }

    // Method to authenticate to kubernetes clusters
    def auth_login(clusterName, zone, projectID){
        jenkins.sh """
            echo "********************* Entering into kubernetes Authentication/login Method *********************"
            gcloud compute instances list
            echo "********************* Create the config file for the environment *********************"  
            gcloud container clusters get-credentials $clusterName --zone $zone --project $projectID
            kubectl get nodes 
        """
    }

    // Method to deploy the application. 
    def k8sdeploy(fileName, docker_image, namespace){
        jenkins.sh """
            echo "********************* Entering into kubernetes Deployment Method *********************"
            sed -i "s|DIT|${docker_image}|g" ./.cicd/${fileName}
            kubectl apply -f ./.cicd/${fileName} -n $namespace
        """
    }
        def k8sHelmChartDeploy(appName, env, helmChartPath, imageTag, namespace){ 
        jenkins.sh """
            echo "********************* Entering into kubernetes Helm Deployment Method *********************"
            helm version
        """
    }
}