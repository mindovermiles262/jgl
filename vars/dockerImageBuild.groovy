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
    // stage('Get ENV') {
    //   agent {
    //     kubernetes {
    //       containerTemplate {
    //         name 'alpine'
    //         image 'alpine:latest'
    //         ttyEnabled true
    //         command 'cat'
    //       }
    //     }
    //   }
    //   steps {
    //     script {
    //       sh 'printenv'
    //     }
    //   }
    // }

    // stage('Env') {
    //   steps {
    //     script {
    //       sh "printenv"
    //     }
    //   }
    // }

    stage('BuildProps') {
      steps {
        script {
          buildProps = dataeng.createBuildProps()
        }
      }
    }

    stage('Print BuildProps') {
      steps {
        script {
          buildProps.each { entry -> println "$entry.key => $entry.value" }
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
          // dataeng.dockerImageBuild()
          echo "[*] Inside Build Step"
        }
      }
    }

    stage('tag') {
      steps {
        script {
          dataeng.tagBbRepo()
        }
      }
    }
  }
}
}
