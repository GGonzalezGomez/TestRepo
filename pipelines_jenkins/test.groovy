//###############################################################################
//                                                                              #
// File name      : cicd-euice-talend-dev.groovy                                #
// Creation date  : 10-12-2019                                                  #
// Last modified  : 10-12-2019                                                  #
// Created by     : guillermo.gonzalez@alliance-healthcare.net                  #
// Copyright 2019 Walgreens Boots Alliance, unless otherwise noted.             #
//                                                                              #
// Purpose: Jenkins groovy pipeline file                                        #
//                                                                              #
//###############################################################################

import groovy.json.JsonSlurper
import groovy.json.*
import javax.net.ssl.*

env.PROJECT_GIT_NAME    = 'TEST'
env.ARTIFACT            = 'testjob'
env.VERSION             = '0.1'
env.GIT_URL             = 'https://github.com/GGonzalezGomez/TestTalend.git'
env.DESTREPO            = "snapshots"
env.GIT_CREDENTIALS_ID  = 'git'
env.PROJECT_NAME        = env.PROJECT_GIT_NAME.toLowerCase()
env.tac_url             = "http://192.168.56.102:8181/org.talend.administrator"
env.tac_user            = "test@company.com"
env.tac_pwd             = "admin"
//env.jobname             = "TestJob01"
env.jobname		= "TestJob02"
env.taskDescription     = "This is an automately created task"
env.artifactGroupId     = "org.example.test.job"
env.artifactId          = "testjob"
env.artifactVersion     = "0.1.0"

//--------------------------------------------------------------------------------------------------------------------------------------------------------
//--------------------------------------------------------------------------------------------------------------------------------------------------------
def metaServletCall(tac_url, request){
  def urlSt = "$tac_url/metaServlet?${request.toString().bytes.encodeBase64()}"

  println "    >> MetaServlet request : $request"
  println "    >> MetaServlet url Call: $urlSt"

  def tresp = urlSt.toURL().text

  print "    >> MetaServlet response : "
  println groovy.json.JsonOutput.prettyPrint(tresp)

  def jresp = new groovy.json.JsonSlurper().parseText(tresp)

  println "    >> MetaServlet ReturnCode= $jresp.returnCode"

  return jresp
}

//--------------------------------------------------------------------------------------------------------------------------------------------------------
//--------------------------------------------------------------------------------------------------------------------------------------------------------

node {
    int taskId = -1

    try {

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

        stage('Get Task Id by its name') {

            def ms_request = new groovy.json.JsonBuilder()
            def ms_response
            
            ms_request (
                actionName: "getTaskIdByName",
                authUser  : tac_user,
                authPass  : tac_pwd,
                taskName  : jobname
            )

            ms_response = metaServletCall(tac_url, ms_request)
            println "$ms_response"

            if(ms_response.returnCode != 0){
                if(ms_response.returnCode != 5) { //task does not exist
                    println "   - Error Executing action '${ms_request.getContent().actionName}'. Error= $ms_response.error"
                    println "     - taskName=${jobname}"
                    throw new Exception("Error Executing task '${ms_request.getContent().actionName}'. Error= $ms_response.error")
                }
            }
            else
                taskId = ms_response.taskId
        }
        
    } catch (err) {
        currentBuild.result = 'FAILED'
        throw err
    }
}
