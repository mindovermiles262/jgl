def call(){
  // Emtpy map for pipeline parameters
  def props = [:]

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
      //           name 'dataeng-pytest'
      //           command 'cat'
      //           ttyEnabled true
      //       }
      //     }
      //   }
      //   steps {
      //     script{
      //       dataeng.unitTest()
      //     }
      //   }
      // }

      stage('Import Properties') {
        steps {
          script {
            // load yaml file from app repository
            def localSettings = readYaml(file: "resources/dataeng-props.yml")
            props = dataeng.configLocal(localSettings)
          }
        }
      }
    }
  }
}
