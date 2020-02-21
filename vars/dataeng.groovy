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
  ]

  settings = overwriteMap(defaultSettings, customSettings)

  switch(settings.unitTestLanguage){
  case("python-default"):
      stage('pytest') {
        agent {
          kubernetes {
            containerTemplate {
              image 'python:3.7-alpine'
              name 'python-3.7'
              command 'cat'
              ttyEnabled true
            }
          }
        }
        steps {
          script {
            echo "[+] Running unit tests"
            sh "apk update && apk add make"
            sh "python -v"
            // sh "make -f ${settings['unitTestMakefile']} test"
          }
        }
      }
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
