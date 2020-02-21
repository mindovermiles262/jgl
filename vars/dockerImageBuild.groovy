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

    stage('BuildProps') {
      steps {
        script {
          buildProps = dataeng.createBuildProps()
          // overwrite base container image
          buildProps.unitTestBaseContainer = 'python:3.8-slim'
          buildProps.unitTestInstallMakeCommand = "apt-get update && apt-get install -y make"
        }
      }
    }

    // Runs 'make test' to execute unit testing. Add an optional map config
    // to dataeng.unitTest() to change default values.
    stage('unit test') {
      environment {
        UNIT_TEST_BASE_CONTAINER = 'python:3.8-slim'
        UNIT_TEST_INSTALL_MAKE_COMMAND = 'apt-get update && apt-get install -y make'
      }
      agent {
        kubernetes {
          containerTemplate {
            image "${buildProps.unitTestBaseContainer}"
            name 'unit-test'
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
