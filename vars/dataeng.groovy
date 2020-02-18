#!/usr/bin/env groovy

// Filename:  dataeng.groovy
// Purpose:   Helper methods for DataEng team
// Usage:     Inside of project repository, include Jenkinsfile to pull this
//            library and run 'dataeng()'
//
// Usage Example:
//            library identifier: 'prod-jgl@master', retriever: modernscm(
//              [$class: 'gitscmsource', remote: 'https://github.com/mindovermiles262/jgl'])
//            dataeng()
// Methods:
//   call() => Runs entire build pipeline
//   verifyBranchName() => Checks that branch is 'master', 'feature/*' or 'develop'. Fails if not
//   unitTest( env.GIT_URL,   ) 


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

      stage('Verify Branch Name') {
        steps {
          verifyBranchName() 
        }
      }

      stage('Unit Test') {
        agent {
          kubernetes {
            containerTemplate {
              image 'mindovermiles262/pytest:0.1.2'
              name 'unit-test-python'
              ttyEnabled true
              command 'cat'
            }
          }
        }
        steps {
          unitTest()
        }
      }

    } // Close stages{}
  }
}

def verifyBranchName(String regexPattern = "(^(origin/)?master\$|^feature/.*|^develop\$)") {
  if(env.GIT_BRANCH ==~ /${regexPattern}/) {
    println "Branch ${env.GIT_BRANCH} is valid"
  } else {
    error("[!] Branch ${env.GIT_BRANCH} is invalid")
  }
}


// Runs 'make test' on specified git repository. Pass a map as the second 
// argument for custom settings
def unitTest(Map customSettings = [:]) {
  defaultSettings = [
    unitTestGitUrl: env.GIT_URL
    unitTestGitBranch: env.GIT_BRANCH,
    unitTestMakefile: "Makefile",
    unitTestLanguage: "python",
    unitTestContainer: "unit-test-python"
  ]

  settings = overwriteMap(defaultSettings, customSettings)

  switch(settings.unitTestLanguage){
  case("python"):
    pipeline {
      container(settings.unitTestContainer) {
        checkout([
          $class: 'GitSCM',
          branches: [[name: settings.unitTestGitBranch]],
          userRemoteConfigs: [[url: settings.unitTestGitUrl]]
        ])
        sh "make -f ${settings['unitTestMakefile']} test"
      }
    }
  default:
    // Fail if not 'unitTestLanguage' is not supported
    error("[!] Unit Testing Language not supported.")
  }
}


// Given two maps, append or overwrite the second into the first
def overwriteMap(Map defaultSettings, Map customSettings) {
  customSettings.each{ entry -> defaultSettings[entry.key] = entry.value }
  return defaultSettings
}
