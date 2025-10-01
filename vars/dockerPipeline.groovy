import com.chathura.builds.Docker;

def call(Map pipelineParams) {
    Docker docker = new Docker(this)
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
            APPLICATION_NAME = "eureka"
            SONAR_HOST= 'http://34.172.162.27:9000'
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            DOCKER_HUB = "docker.io/sureshindrala"
            DOCKER_CREDS = credentials('dockerhub_sureshindrala_creds')
            DOCKER_SERVER= "136.114.27.219"

        }
        stages{
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
                    sh 'mvn clean package -DskipTest=true'
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
                            -Dsonar.projectKey=chathura-eureka \
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
                        imageValidation().call()
                        dockerdeploy('dev','5761').call()
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
                        dockerdeploy('test','6232').call()
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
                        dockerdeploy('stage','7232').call()
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
                        dockerdeploy('prod', '8232').call()
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

def dockerdeploy(envDeploy,envPort) {
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
            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${env.DOCKER_SERVER}" "docker container run -dit -p ${envPort}:8761 --name ${env.APPLICATION_NAME}-${envDeploy} ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$USERNAME"@"${env.DOCKER_SERVER}" "docker ps"
            """
        } catch (err) {
            echo "Error caught: ${err}"
            }
        }
    }
}


