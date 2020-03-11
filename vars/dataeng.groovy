#!/usr/bin/env groovy

/*
This file, dataeng.groovy, is a shared helper library for Jenkins. Inside contains
all of the helper functions for simplifying DataEngineering's pipelines.

You can call a function from this library by simply calling dataeng.methodName()
inside of the pipeline file.
*/


def createBuildProps() {
  // Set calendar and get week number and year
  Calendar date = Calendar.getInstance();
  def weekNumber = date.get(Calendar.WEEK_OF_YEAR);
  def year = date.get(Calendar.YEAR);
  def currentWeekNumber = "00"

  // Set week number to current week, but need to preface week numbers less than 10 with a 0
  if (weekNumber >= 9) {
      currentWeekNumber = (weekNumber)
  }
  else {
      currentWeekNumber = "0" + (weekNumber)
  }

  buildProps = [:]
  buildProps.emails = 'me@myself.com'
  buildProps.jobPath = env.JOB_NAME.split('/')
  buildProps.repoName = buildProps['jobPath'][-2]
  buildProps.branch = env.GIT_BRANCH
  buildProps.commit = env.GIT_COMMIT[-6..-1]
  buildProps.buildNumber = env.BUILD_NUMBER
  buildProps.buildId = '#' + buildProps['repo'] + '_' + buildProps['branch'] + ' - ' + buildProps['commit']
  buildProps.slackNotifyChannel = '#dataeng-alerts'
  buildProps.currentYear = "${year}"
  buildProps.nextRelease = "${currentWeekNumber}"
  buildProps.cloudBuildLogsBucket = 'gs://aduss-kubicia-cloud-build-bucket/logs/'
  buildProps.bbOrgPath = "bitbucket.org:443/myrepo/"
  buildProps.bbSvcId = "bb-service-user-pass"
  
  // environment specific "named" properties so they can be referenced explicitly
  // dev
  buildProps.gcpCredentialsIdDev = 'svc-aduss-kubicia-cloudbuilder'
  buildProps.gcpProjectIdDev = 'aduss-kubicia'
  // prod
  buildProps.gcpCredentialsIdProd = 'svc-aduss-kubicia-cloudbuilder'
  buildProps.gcpProjectIdProd = 'aduss-kubicia'
  

  // branch specific props
  if (buildProps.branch ==~ /^(^master$)/) {
    buildProps.environment = 'prod'
    buildProps.gcpProjectId = 'aduss-kubicia'
    buildProps.gcpCredentialsId = buildProps.gcpCredentialsIdProd
    buildProps.imageReleaseState = ''

  } else {
    buildProps.environment = 'dev'
    buildProps.gcpProjectId = 'aduss-kubicia'
    buildProps.gcpCredentialsId = buildProps.gcpCredentialsIdDev
    buildProps.imageReleaseState = "-beta"
  }

  // helm specific props
  buildProps.helmReleaseName = buildProps.repoName

  // grab some needed information from our helm values file for the current environment
  // We assume the following properties come from values-[ENV].yaml:
  //   * targetGkeCluster
  //   * targetGkeClusterZone
  def valuesFile = "values-${buildProps.environment}.yaml"
  if (fileExists(valuesFile)) {
    def helm_values = readYaml file: valuesFile
    buildProps.targetGkeCluster = helm_values.global.targetGkeCluster
    buildProps.targetGkeClusterZone = helm_values.global.targetGkeClusterZone
    buildProps.clusterNamespace = helm_values.global.clusterNamespace
  }

  // Overwrite any values from buildProps.yaml
  // We assume the following properties come from buildProps.yaml:
  //   * imageVersion
  def buildPropsFileName = "buildProps.yaml"
  if (fileExists(buildPropsFileName)) {
    echo "[*] Loading build props from ${buildPropsFileName}"
    customProps = readYaml file: buildPropsFileName
    buildProps = overwriteMap(buildProps, customProps)
  }

  // Compound buildProps must go last so overwritten buildProps are interpolated
  if (buildProps.imageVersion) {
    buildProps.imageTag = buildProps.imageVersion + buildProps.imageReleaseState
  } else {
    buildProps.imageTag = buildProps.buildNumber + buildProps.imageReleaseState
  }
  buildProps.containerImageName = "gcr.io/${buildProps.gcpProjectIdProd}/${buildProps.repoName}:${buildProps.imageTag}"
  buildProps.containerImageLatest = "gcr.io/${buildProps.gcpProjectIdProd}/${buildProps.repoName}:latest"
  buildProps.gcpKeyFile = credentials("${buildProps.gcpCredentialsId}")

  return buildProps
}


// Given two maps, append or overwrite the second into the first. Helper function.
def overwriteMap(Map receptorMap, Map donorMap) {
  donorMap.each{ entry -> receptorMap[entry.key] = entry.value }
  return receptorMap
}


// Submits Dockerfile to CloudBuild for container creation
def gcloudBuildSubmit() {
  echo "[+] Submitting build for ${buildProps.containerImageName}"
  dir ('./src/') {
    sh """
    gcloud \
      --quiet \
      --project ${buildProps.gcpProjectId} \
      builds submit \
      --tag ${buildProps.containerImageName} \
      --gcs-log-dir=${buildProps.cloudBuildLogsBucket}
    """
  }
}


// Checks if container name is already in GCR
def gcloudCheckIfImageExists() {
  def existingImageExitCode = sh(
    returnStatus: true,
    script: "gcloud container images describe ${buildProps.containerImageName}"
  )
  if (existingImageExitCode == 0) {
    // exit(0) => Image found in GCR. Bail on build
    error("Image ${buildProps.containerImageName} already exists in GCP. Refusing to build")
  } else { 
    echo "[+] Image not found in GCR. Proceeding with build."
  }
}


// Checks if branch name is 'master' or 'feature/*'. Fails if not.
def verifyBranchName(String regexPattern = "(^master\$|^feature/.*|^develop\$)") {
  if(env.BRANCH_NAME ==~ /${regexPattern}/) {
    echo "Branch ${env.BRANCH_NAME} is valid"
  } else {
    error("[FAILED] Branch ${env.BRANCH_NAME} is INVALID")
  }
}


def helmLint() {
  // ********************************************************************************
  // this method assumes the use of a container that has the helm utility installed!!
  // ********************************************************************************

  // find all helm charts in the "charts/" subdirectory (if there are any)
  // this includes subcharts of subcharts (i.e. charts/chart1/charts/chart1_1)
  chart_paths = findHelmCharts()

  // lint and render templates for the "root" chart in the repo
  chart_paths.add('.')

  // iterate over each chart and perform linting and template rendering
  chart_paths.each { path ->
    dir(path){
      echo "Linting Chart in ${path}"
      sh "helm lint ."
      sh "helm template ."
    }
  }
}


helmDeploy() {
  // ********************************************************************************
  // this method assumes the use of a container that has the helm and
  // gcloud utilities installed!!
  // ********************************************************************************

  // authenticate to GCP
  gcloudAuth()

  // setup kubeconfig using the gcloud helper
  sh """
    gcloud \
      --project=${buildProps.gcpProjectId} \
      container clusters get-credentials ${buildProps.targetGkeCluster} \
      --zone=${buildProps.targetGkeClusterZone} \
      --project=${buildProps.gcpProjectId}
  """

  // deploy
  sh "helm upgrade --install --wait ${buildProps.helmReleaseName} ."
}


// ******************************
// private methods
// ******************************

// generate a List object of all helm chart directories
def findHelmCharts() {
  try {
    def List chart_paths = []
    // find all directories that contain a Chart.yaml file
    def foundFiles = findFiles(glob: 'charts/**/Chart.yaml')

    foundFiles.each { file -> 
      // get the full path list, remove the Chart.yaml file and rejoin it
      // to get the actual chart path
      def List path_list = file.path.split('/')
      path_list.remove(path_list.size() - 1)
      def String chart_path = path_list.join('/')

      // append to our chart_paths List object
      chart_paths.add(chart_path)
    }
  }
  catch (err) {
    def failureMessage = 'Unable to find helm chart subdirectories for linting.'
    echo "${failureMessage}" + ": " + err
    currentBuild.result = 'FAILURE'
    dehpSendNotifications currentBuild.result 
    throw err
  }

  return chart_paths
}

// Authenticates Service Account
def gcloudAuth() {
  echo "[+] Authenticating to gCloud with ${buildProps.gcpCredentialsId}"
  // Upload the Service Account .json as a 'Secret File' in Jenkins Credential Manager
  withCredentials([file(credentialsId: buildProps.gcpCredentialsId, variable: 'gcpKeyFile')]) {
    sh """
    gcloud auth activate-service-account \
      --key-file=${gcpKeyFile} \
      --project=${buildProps.gcpProjectId}
    """
  }
}

def tagBbRepo(String tag = buildProps.imageTag) {
  withCredentials([usernamePassword(
    credentialsId: buildProps.bbSvcId, variable: 'USERPASS', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
      // Tags repo with docker image name, pushes to BitBucket
      sh """
        git config user.email "jenkins-gcp-noreply@zoro.com"
        git config user.name "GCP Jenkins"

        # git tag -af ${tag} -m 'Docker Image Build Commit'
      """

      // Pushes to new bitbucket branch (named same as container image)
      def bbRepoUrl = "https://" + USERPASS + "@" \
        + buildProps.bbOrgPath + buildProps.repoName + ".git"
      
      echo "[*] bbRepoUrl => ${bbRepoUrl}"
      echo "[*] Username => ${USERNAME}"
      echo "[*] Password => ${PASSWORD}"
  }
}


def dockerImageBuild() {
  gcloudAuth()
  gcloudCheckIfImageExists()
  gcloudBuildSubmit()
}
