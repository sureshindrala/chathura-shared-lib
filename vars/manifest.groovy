import com.chathura.builds.Docker;
import com.chathura.k8s.K8s;

def call(Map pipelineParams) {
    Docker docker = new Docker(this)
    K8s k8s = new K8s(this)


    pipeline {
        agent {
            label 'k8s-slave'
        }
        tools {
            maven 'Maven-3.9.11'
            jdk 'JDK-17'
        }
        parameters {
            choice (name: 'scanOnly',
                    choices: 'no\nyes',
            )
            choice (name: 'buildOnly',
                choices: 'no\nyes',
            )
            choice (name: 'dockerPush',
                choices: 'no\nyes',
            )
            choice (name: 'deployToDev',
                choices: 'no\nyes',
            )
            choice (name: 'deployToTest',
                choices: 'no\nyes',
            )
            choice (name: 'deployToStage',
                choices: 'no\nyes',
            )
            choice (name: 'deployToProd',
                choices: 'no\nyes',
            )                                            
        }
        parameters {
            string(name: 'NAMESPACE_NAME', description: "Enter the name of the namespace, you want to create")
        }        
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            // the below are hostports
            DEV_HOST_PORT = "${pipelineParams.devHostPort}"
            TST_HOST_PORT = "${pipelineParams.tstHostPort}"
            STG__HOST_PORT = "${pipelineParams.stgHostPort}"
            PROD__HOST_PORT = "${pipelineParams.prdHostPort}"
            CONT_PORT = "${pipelineParams.contPort}"
            SONAR_HOST= 'http://34.172.162.27:9000'
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            DOCKER_HUB = "docker.io/sureshindrala"
            DOCKER_CREDS = credentials('dockerhub_sureshindrala_creds')
            DOCKER_SERVER= "136.114.27.219"

        // *******Kubernetes cluster**********
            DEV_CLUSTER_NAME = "chathura-cluster"
            DEV_CLUSTER_ZONE = "us-central1-a"
            DEV_PROJECT_ID = "chathura-project"

        //*******KUBERNETES yml FILE************
            K8S_DEV_FILE = "k8s_dev.yml"
            K8S_TST_FILE = "k8s_test.yml"
            K8S_STG_FILE = "k8s_stage.yml"
            K8S_PRD_FILE = "k8s_prod.yml"

        // *******KUBERNETES NAMESPACES*******
            DEV_NAMESPACE = "dev-cart-ns"
            TST_NAMESPACE = "test-cart-ns"
            STG_NAMESPACE = "stage-cart-ns"
            PRD_NAMESPACE = "prod-cart-ns"
            // Environment Details
            DEV_ENV = "dev"
            TST_ENV = "test"
            STG_ENV = "stage"
            PRD_ENV = "prod"
        
        // Chart details
            HELM_CHART_PATH = "${workspace}/chathura-shared-lib/chart"
            
        }
        stages{
            stage('CheckoutSharedLib'){
                steps {
                    script {
                        k8s.gitClone()
                    }
                }
            }            
            stage('Authentication'){
                steps{
                    script{
                        echo "**************k8s-login to cluster*********************"
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")                        
                    }
                }
            }
            stage('Create K8S Namespace'){
                steps {
                    script {
                        k8s.namespace_creation("${params.NAMESPACE_NAME}") 
                    }
                }
    
            }
            post {
                always {
                    echo "Cleaning up the chathura-shared-lib directory"
                    script {
                        def sharedLibDir = "${workspace}/chathura-shared-lib"
                        if (fileExists(sharedLibDir)) {
                            echo "Deleting the shared library directory: ${sharedLibDir}"
                            sh "rm -rf ${sharedLibDir}"
                        }
                        else {
                            echo "Shared library directory does not exist: ${sharedLibDir}, seems already cleandup"
                        }
                    }
                }
        }   }                                    
    }   
}
