#!/usr/bin/env groovy

/*
This file, dataeng.groovy, is a shared helper library for Jenkins. Inside contains
all of the helper functions for simplifying DataEngineering's pipelines.

You can call a function from this library by simply calling dataeng.methodName()
inside of the pipeline file.
*/

// Checks if branch name is 'master', 'feature/*', or 'develop'. Fails build if not.
def verifyBranchName(String regexPattern = "(^master\$|^feature/.*|^develop\$)") {
  if(env.GIT_BRANCH ==~ /${regexPattern}/) {
    println "Branch ${env.GIT_BRANCH} is valid"
  } else {
    error("[FAILED] Branch ${env.GIT_BRANCH} is INVALID")
  }
}


// Runs unit testing on codebase. 
// By default, runs 'make test' from the project's root directory inside a
// sandboxed testing container.
def unitTest(Map customSettings = [:]) {
  defaultSettings = [
    unitTestGitUrl: env.GIT_URL,
    unitTestGitBranch: env.GIT_BRANCH,
    unitTestMakefile: "Makefile",
    unitTestLanguage: "python-default",
    unitTestContainerName: "dataeng-pytest"
  ]

  settings = overwriteMap(defaultSettings, customSettings)

  switch(settings.unitTestLanguage){
  case("python-default"):
    script {
      checkout([
        $class: 'GitSCM',
        branches: [[name: settings.unitTestGitBranch]],
        userRemoteConfigs: [[url: settings.unitTestGitUrl]]
      ])
      sh "make -f ${settings['unitTestMakefile']} test"
    }
  default:
    // Fail if not 'unitTestLanguage' is not supported
    error("[!] Unit Testing Language not supported.")
  }
}


// Given two maps, append or overwrite the second into the first. Helper function.
def overwriteMap(Map defaultSettings, Map customSettings) {
  customSettings.each{ entry -> defaultSettings[entry.key] = entry.value }
  return defaultSettings
}

def createBuildProps() {
  // create the initial map from the local buildProps.yaml file if it exists
  // we assume the following properties come from buildProps.yaml:
  // * imageVersion
  def buildPropsFileName = "buildProps.yaml"
  def buildPropsFile = new File(buildPropsFileName)
  if (buildPropsFile.exists()) {
    buildProps = readYaml file: buildPropsFileName
  } else {
    buildProps = [:]
  }

  buildProps.gcpProjectId = 'kubicia'
  buildProps.repoName = env.JOB_BASE_NAME
  buildProps.imageTag = env.BUILD_ID
  buildProps.containerImageName = "gcr.io/${ buildProps.gcpProjectId}/${buildProps.repoName}:${buildProps.imageTag}"
  return buildProps
}

def gcloudAuth(){
  echo "[+] Authenticating with ${buildProps.gcpCredentials}"
  sh """
  gcloud auth activate-service-account \
    --key-file=${GC_KEY} \
    --project=${buildProps.gcpProjectId}
  """
}

def buildDockerImage() {
  echo "[+] Building Docker Image"
  dir ('./lib/') {
    sh """
    gcloud \
      --quiet \
      --project ${buildProps.gcpProjectId} \
      builds submit \
      --tag ${buildProps.containerImageName}
    """
  }
}
