import groovy.json.*
import javax.net.ssl.*

//def env = System.getenv()

//env.each { k, v ->
//    println "$k = $v"
//}

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
    println "Starting..."
    def tac_url             = "http://192.168.56.102:8181/org.talend.administrator"
    def tac_user            = "test@company.com"
    def tac_pwd             = "admin"
    def jobname             = "TestJob01"
    def taskDescription     = "This is an automately created task"
    def artifactGroupId     = "org.example.test.job"
    def artifactId          = "testjob"
    def artifactVersion     = "0.1.0"

    int taskId = -1
    
    try {
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

        stage('Update or create task'){
            
            def ms_request = new groovy.json.JsonBuilder()
            def ms_response

            if(taskId != - 1){  // Task exists so we need to update it
                println "Task already exists, updating the artifact"
                ms_request (
                    actionName              : "updateTask",
                    authUser                : tac_user,
                    authPass                : tac_pwd,
                    taskName                : jobname,
                    "taskId"                : taskId,
                    "artifactoryRepository" : "snapshots",
                    "artifactoryGroupId"    : artifactGroupId,
                    "artifactoryArtifactId" : artifactId,
                    "artifactoryVersion"    : artifactVersion
                )
    
                println "Update task request: $ms_request"
                ms_response = metaServletCall(tac_url, ms_request)
                println "Update action response: $ms_request"
            }
            else {              // Task doesn't exist yet we need to create it
                println "Task doesn't exist, creating it"
                ms_request(
                    actionName              : "associatePreGeneratedJob",
                    authUser                : tac_user,
                    authPass                : tac_pwd,
                    taskName                : jobname,
                    active                  : true,
                    description             : taskDescription,
                    "artifactoryRepository" : "snapshots",
                    "artifactoryGroupId"    : artifactGroupId,
                    "artifactoryArtifactId" : artifactId,
                    "artifactoryVersion"    : artifactVersion,
                    "importType"            : "Artifactory",
                    "logLevel"              : "Info",
                    "executionServerName"   : "LocalJS01",
                    "onUnknownStateJob"     : "WAIT",
                    "pauseOnError"          : false,
                    "timeout"               : 3600,
                    "taskType"              : "Artifact"
                )
                println "Create task request: $ms_request"
                ms_response = metaServletCall(tac_url, ms_request)
                println "Create task action response: $ms_request"
            }

            if(ms_response.returnCode != 0){
                println "There was an error with the task update/creation"
                println "   - Error Executing action '${ms_request.getContent().actionName}'. Error= $ms_response.error"
                println "     - taskName=${jobname}"
                throw new Exception("Error Executing task '${ms_request.getContent().actionName}'. Error= $ms_response.error")
            }
            else {
                println "Task updated/created successfully!"
            }

            if(taskId == - 1){          // Task has been created but we need the task id for requesting the deployment

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

        }

        stage('Request deployment'){
            def ms_request = new groovy.json.JsonBuilder()
            def ms_response
            
            ms_request (
                actionName: "requestDeploy",
                authUser  : tac_user,
                authPass  : tac_pwd,
                "taskId"  : taskId
            )
            
            println "Requesting deployment: $ms_request"
            ms_response = metaServletCall(tac_url, ms_request)
            
            println "Deployment response: $ms_request"
            if(ms_response.returnCode == 0){
                println "Deployment successful!"
            }
            else {
                println "There was an error with the deployment"
                println "   - Error Executing action '${ms_request.getContent().actionName}'. Error= $ms_response.error"
                println "     - taskName=${jobname}"
                throw new Exception("Error Executing task '${ms_request.getContent().actionName}'. Error= $ms_response.error")
            }
        }

    }
    catch (err) {
        currentBuild.result = 'FAILED'
        throw err
    }
}
