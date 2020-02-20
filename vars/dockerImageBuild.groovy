def call(){

  pipeline {

    agent {
      kubernetes {
        // containerTemplate {
        //   name 'alp'
        //   image 'alpine'
        //   ttyEnabled true
        //   command 'cat'
        // }
        containerTemplate {
          image 'google/cloud-sdk:alpine'
            name 'docker-build'
            command 'cat'
            ttyEnabled true
        }
      }
    }

    stages {
      stage('Set Build Properties') {
        steps {
          script {
            buildProps = dataeng.createBuildProps()
          }
        }
      }
      // List ENV vars for easier debugging
      // stage('Get ENV') {
      //   steps {
      //     script {
      //       sh 'printenv'
      //     }
      //   }
      // }

      // immediately fail the job when someone is working
      // with a branch we know nothing about.
      // stage('verify branch') {
      //   steps {
      //     script {
      //       dataeng.verifyBranchName("(^(origin/)?master\$|^feature/.*|^develop\$)")
      //     }
      //   }
      // }

      // Runs 'make test' to execute unit testing. Add an optional map config
      // to dataeng.unitTest() to change default values.
      // stage('unit test') {
      //   agent {
      //     kubernetes {
      //       containerTemplate {
      //         // TODO: Upload python testing container to GCR with 'make' installed
      //         image 'mindovermiles262/pytest:0.2.0'
      //         name 'dataeng-pytest'
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

      stage('Auth') {
        steps {
          container('docker-build') {
            script {
              dataeng.gcloudAuth()
            }
          }
        }
      }
      stage('Verify') {
        steps {
          container('docker-build') {
            script {
              dataeng.gcloudCheckIfImageExists()
            }
          }
        }
      }
      stage('Build') {
        steps {
          container('docker-build') {
            script {
              dataeng.gcloudBuildSubmit()
            }
          }
        }
      }
    }
  }
  }
