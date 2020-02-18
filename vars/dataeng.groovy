#!/usr/bin/env groovy

def configGlobal(String envName = 'test',
                 String namespace = 'default',
                 String yamlFile = 'dataeng-env.yml') {
  /*
  Use this method to set global ENV Variables

  TODO: Write this Method.

  Tried to use a similar method to configLocal() but could not set global
  ENV Variables from inside this method. Should look into exporting an 
  'environment {}' block and loading that from the Jenkinsfile

  */
}

def configLocal(String envName = 'test',
                String namespace = 'default',
                String yamlFile = 'dataeng-props.yml') {
  def propsFile = libraryResource yamlFile // loads from ../resources directory
  def config = readYaml text: propsFile
  def props = [:]
  config['environments'][envName][namespace].each {
    props[it.key] = it.value
  }
  return props
}


def verifyBranch(String branchName,
                 String regexPattern = "(^master\$|^feature/.*)") {
  if(branchName ==~ /${regexPattern}/) {
    println "Branch ${branchName} is valid"
  } else {
    println "[FAILED] Branch ${branchName} is INVALID"
    println "REGEX: ${regexPattern}"
    // error('[FAIL] Branch is Invalid')
  }
}

def verifyBranchName(String regexPattern = "(^master\$|^feature/.*|^develop\$)") {
  if(env.GIT_BRANCH ==~ /${regexPattern}/) {
    println "Branch ${env.GIT_BRANCH} is valid"
  } else {
    error("[!] Branch ${env.GIT_BRANCH} is invalid")
  }
}

def overwriteMap(Map defaultSettings, Map customSettings) {
  customSettings.each{ entry -> defaultSettings[entry.key] = entry.value }
  return defaultSettings
}

// Runs 'make test' on specified git repository. Defaults to a python testing
// environment. Pass a map as the second argument for custom settings
def unitTest(String unitTestGitUrl,
             Map customSettings = [:]) {
  defaultSettings = [
    unitTestGitBranch: "*/master",
    unitTestMakefile: "Makefile",
    unitTestLanguage: "python",
    unitTestContainer: "unit-test-python"
  ]
  settings = overwriteMap(defaultSettings, customSettings)
  // settings.each{ e -> println("$e.key => $e.value")}
  switch(settings['unitTestLanguage']){
  case("python"):
    pipeline {
      container(settings['unitTestContainer']) {
        checkout([$class: 'GitSCM', branches: [[name: settings['unitTestGitBranch']]],
            userRemoteConfigs: [[url: unitTestGitUrl]]])
        sh "make -f ${settings['unitTestMakefile']} test"
      }
    }
  default:
    error("[!] Unit Testing Language not supported.")
  }
}
