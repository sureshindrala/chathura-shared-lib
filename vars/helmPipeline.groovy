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
            
        }
        stages{
            // stage('Authentication') {
            //     steps{
            //         echo "*********Authentication GKE*****************"
            //         script {
            //             k8s.auth_login("${env.DEV_CLUSTER_NAME}","${env.DEV_CLUSTER_ZONE}","${env.DEV_PROJECT_ID}")
            //         }
            //     }
            // }
            stage('Build'){
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'
                        }
                    }
                }
                steps {
                    echo "*********************Build ${env.APPLICATION_NAME}*************************"
                    sh 'mvn clean package -DskipTests=true'
                    archive 'target/*.jar'
                }

            }
            stage('sonarqube'){
                when {
                    anyOf{
                        expression{
                            params.dockerPush == 'yes'
                            params.scanOnly == 'yes'
                        }
                    }
                }
                steps {
                    echo "***************Build ${env.APPLICATION_NAME}-Sonar***************************"
                    withCredentials([string(credentialsId: 'sonar_creds', variable: 'sonar_creds')]) {
                        sh """
                            mvn sonar:sonar \
                            -Dsonar.projectKey=chathura-${env.APPLICATION_NAME} \
                            -Dsonar.host.url=$SONAR_HOST \
                            -Dsonar.login=$sonar_creds
                        """

                    }        
                }
    
            }
            // stage ('Build Format') {
            //         steps {
            //             echo "***************************Printing Build Format*****************************"
            //             script {
            //                 sh """
            //                 echo "Testing JAR SOURCE: chathura-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING}"
                            
                            

            //                 """
            //                 // sh "cp ${workspace}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
            //                 // sh "ls -la ./.cicd"
            //                 // sh "docker build --force-rm --no-cache --pull --rm=true --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd "
            //             }
            //         }
            //     }
            stage ('docker build and push') {
                when {
                    anyOf {
                        expression {
                            params.dockerBuildandPush == 'yes'
                            
                        }
                    }
                }
                steps{
                    script{
                        dockerBuildandPush().call()
                    }
                }
            }
            stage('docker deploy-dev') {
                when {
                    anyOf {
                        expression {
                            params.deployToDev == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        // this will create docker image///
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        
                        echo "**************k8s-login to cluster*********************"
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")

                        // this will validate the image

                         
                        imageValidation().call()                                         

                        echo "************Deploying Using Helm Charts****************" 
                          k8sHelmChartDeploy()
                        //k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "${DEV_ENV}", "${HELM_CHART_PATH}", "${GIT_COMMIT}", "${env.DEV_NAMESPACE}")

                        //k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")

                        // dockerdeploy('dev', "${env.DEV_HOST_PORT}", "${env.CONT_PORT}").call()
                    }
                }
            }
            stage('docker deploy-test') {
                when {
                    anyOf{
                        expression{
                            params.deployToTest == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        imageValidation().call()
                        dockerdeploy('dev', "${env.TST_HOST_PORT}", "${env.CONT_PORT}").call()
                    }
                }
            }
            stage('docker deploy-stage') {
                when {
                    allOf{
                    anyOf{
                        expression{
                                params.deployToStage == 'yes'
                            }
                        }
                        anyOf {
                            branch 'release/*'
                            tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}\\", comparator: "REGEXP"
                        }  
                    }

                }
                steps {
                    script {
                        imageValidation().call()
                        dockerdeploy('dev', "${env.STG__HOST_PORT}", "${env.CONT_PORT}").call()
                    }
                }
            }
            stage('docker deploy-prod') {
                when {
                    allOf{
                    anyOf{
                            expression{
                                params.deployToProd == 'yes'
                        }
                    }
                        anyOf {
                            tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP" // v1.2.3 is the correct one, v123 is the wrong one
                        }                
                    }

                }
                steps {
                    timeout(time: 300, unit: 'SECONDS') { // SECONDS, MINUTES, HOURS
                        input message: "Deploying ${env.APPLICATION_NAME} to production ??", 
                            ok: 'Yes', 
                            submitter: 'suresh'
                    }
                    script {
                        dockerdeploy('dev', "${env.PROD__HOST_PORT}", "${env.CONT_PORT}").call()
                    }
                }
            }                        
        }
    }
                
    }
def buildApp(){
    return {
        echo "Building ${env.APPLICATION_NAME} Application"
        sh 'mvn clean package -DSkipTests=true'
    }
}



def imageValidation() {
    return {
        println("**************Attempting pull the docker image**********")
        try {
            sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("*************docker image pulled succesfully*************")
        }
        catch(Exception e) {
            println("*************OOPS..!*****The docker image with this tag is not avaliable in this repo, So creating the Image****")
            buildApp().call()
            dockerBuildandPush().call()

        }
    }
}

def dockerBuildandPush() {
    return {
        echo "*****************building Docker image***********************"
        sh """
            cp ${workspace}/target/chathura-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd
            ls -la ./.cicd
            docker build --force-rm --no-cache --pull --rm=true --build-arg JAR_SOURCE=chathura-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}  ./.cicd
            echo "***********Docker login***********************"
            docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW} 
            docker push ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}


        """        
    }
}

def dockerdeploy(envDeploy,envPort,conPort) {
    return{
    withCredentials([usernamePassword(credentialsId: 'docker_vm_creds', 
        passwordVariable: 'PASSWORD', 
        usernameVariable: 'USERNAME')]) {
        try {
            // Stop existing container
            sh """
            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${env.DOCKER_SERVER}" "docker stop ${env.APPLICATION_NAME}-${envDeploy} || true"
            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${env.DOCKER_SERVER}" "docker rm ${env.APPLICATION_NAME}-${envDeploy} || true"
            """

            // Run new container
            sh """
            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${env.DOCKER_SERVER}" "docker container run -dit -p ${envPort}:${conPort} --name ${env.APPLICATION_NAME}-${envDeploy} ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${env.DOCKER_SERVER}" "docker ps"
            """
        } catch (err) {
            echo "Error caught: ${err}"
            }
        }
    }
}