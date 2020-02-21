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


// Runs unit testing on codebase via 'make test'
def unitTest(Map customSettings = [:]) {
  defaultSettings = [
    unitTestMakefile: "Makefile",
    unitTestLanguage: "python",
  ]

  settings = overwriteMap(defaultSettings, customSettings)

  switch(settings.unitTestLanguage){
  case("python"):
    pipeline {
      agent {
        kubernetes {
          containerTemplate {
            image buildProps.unitTestBaseContainer
            name 'python-testing-container'
            command 'cat'
            ttyEnabled true
          }
        }
      }
      stages {
        stage('Python Testing') {
          script {

            echo "[+] Running unit tests"
            sh buildProps.unitTestInstallMakeCommand
            sh "make -f ${settings['unitTestMakefile']} test"

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
    customSettings.each{ entry -> defaultSettings[entry.key] = entry.value 
  }
  return defaultSettings
}


def createBuildProps() {
  // without 'def', buildProps is globally scoped
  buildProps = [:]
  buildProps.branch = env.GIT_BRANCH
  buildProps.commit = env.GIT_COMMIT[-6..-1]
  buildProps.buildNumber = env.BUILD_NUMBER
  buildProps.environment = 'prod'


  buildProps.unitTestBaseContainer = env.UNIT_TEST_BASE_CONTAINER || 'python:3.7-alpine'
  buildProps.unitTestInstallMakeCommand = env.UNIT_TEST_INSTALL_MAKE_COMMAND || 'apk update && apk add make'
  return buildProps
}
