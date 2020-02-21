def call(){

pipeline {

  agent {
    kubernetes {
      containerTemplate {
        name 'gcloud'
        image 'google/cloud-sdk:267.0.0-alpine'
        ttyEnabled true
        command 'cat'
      }
    }
  }

  stages {
    // List ENV vars for easier debugging
    stage('Get ENV') {
      steps {
        script {
          sh 'printenv'
        }
      }
    }

    // Runs 'make test' to execute unit testing. Add an optional map config
    // to dataeng.unitTest() to change default values.
    stage('unit test') {
      agent {
        kubernetes {
          containerTemplate {
            image 'python:3.7-alpine'
            name 'python-37'
            command 'cat'
            ttyEnabled true
          }
        }
      }
      steps {
        script{
          dataeng.unitTest()
        }
      }
    }
  }
}
}
