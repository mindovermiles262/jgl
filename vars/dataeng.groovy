#!/usr/bin/env groovy

def call() {
  pipeline {
    agent {
      kubernetes {
        containerTemplate {
          image 'alpine'
          name 'alpine'
          ttyEnabled true
          command 'cat'
        }
      }
    }
    stages {
      stage('Print Env') {
        steps {
          script {
            sh 'printenv'
          }
        }
      }
    }
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
