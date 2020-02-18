#!/usr/bin/env groovy

// Filename:  buildPipeline.groovy
// Purpose:   Builds a docker image

def call() {
  @Library('prod-jgl') _
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
          script {
            dataeng.verifyBranchName()
          }
        }
      }

      stage('Unit Test') {
        agent {
          kubernetes {
            containerTemplate {
              image 'mindovermiles262/pytest:0.2.0'
              name 'dataeng-pytest'
              command 'cat'
              ttyEnabled true
            }
          }
        }
        steps {
          script {
            dataeng.unitTest()
          }
        }
      }

    } // Close stages{} block
  }
}
