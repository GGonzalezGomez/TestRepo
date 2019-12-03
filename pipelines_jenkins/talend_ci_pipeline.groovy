import groovy.json.JsonSlurper

env.PROJECT_GIT_NAME = 'TEST'
env.PROJECT_NAME = env.PROJECT_GIT_NAME.toLowerCase()
env.ARTIFACT = 'testjob'
env.VERSION = '0.2'
env.GIT_URL = 'https://github.com/GGonzalezGomez/TestTalend.git'
env.TYPE = "" // if big data = _mr
env.IMAGE_NAME = 'cicdtalendtj01'

// Credentials IDs (Manage Jenkins => Credentials)
env.GIT_CREDENTIALS_ID = 'git'
//env.DOCKER_CREDENTIALS_ID = 'docker'

node {
 	// Clean workspace before doing anything
    try {
        def userInput
        def deployprod
        stage('Initialize') {
            sh '''
                echo "PATH = ${PATH}"
                echo "M2_HOME = ${M2_HOME}"
            ''' 
        }
        stage ('Git Checkout') {
            git(
                url: "${GIT_URL}",
                credentialsId: "${GIT_CREDENTIALS_ID}",
                branch: 'master'
            )       
            mvnHome = tool 'M3'
        }
        stage ('Build, Test and Publish artifacts to artifact repository') {
                    withMaven(
                            // Maven installation declared in the Jenkins "Global Tool Configuration"
                            maven: 'M3',
                            // Maven settings.xml file defined with the Jenkins Config File Provider Plugin
                            // Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
                            mavenSettingsConfig: 'maven-file',
                            mavenOpts: '-Dproduct.path=/mnt/cmdline -Dgeneration.type=local -DaltDeploymentRepository=snapshots::default::http://192.168.56.102:8081/artifactory/snapshots/ -Xms1024m -Xmx3096m') 
                            {
                    
                        // Run the maven build
                        sh "mvn -f $PROJECT_GIT_NAME/poms/pom.xml clean deploy -fn -e -pl jobs/process${TYPE}/${ARTIFACT}_${VERSION} -am"
                    
                        }    
        }
    } catch (err) {
        currentBuild.result = 'FAILED'
        throw err
    }
}
