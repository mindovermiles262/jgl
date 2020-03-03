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
      agent {
        kubernetes {
          containerTemplate {
            name 'alpine'
            image 'alpine:latest'
            ttyEnabled true
            command 'cat'
          }
        }
      }
      steps {
        script {
          // sh 'printenv'
          sh 'cat /etc/*-release'
        }
      }
    }

    stage('BuildProps') {
      steps {
        script {
          buildProps = dataeng.createBuildProps()
        }
      }
    }

    // Runs 'make test' to execute unit testing. Add an optional map config
    // to dataeng.unitTest() to change default values.
    // stage('unit test') {
    //   agent {
    //     kubernetes {
    //       containerTemplate {
    //         image "${buildProps.unitTestBaseContainer}"
    //         name 'unit-test'
    //         command 'cat'
    //         ttyEnabled true
    //       }
    //     }
    //   }
    //   steps {
    //     script{
    //       dataeng.unitTest()
    //     }
    //   }
    // }

    stage('build') {
      steps {
        script {
          dataeng.dockerImageBuild()
        }
      }
    }
  }
}
}
