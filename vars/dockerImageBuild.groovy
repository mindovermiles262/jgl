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

    // immediately fail the job when someone is working
    // with a branch we know nothing about.
    stage('verify branch') {
      steps {
        script {
          dataeng.verifyBranchName()
        }
      }
    }

    // Runs 'make test' to execute unit testing. Add an optional map config
    // to dataeng.unitTest() to change default values.
    stage('unit test') {
      agent {
        kubernetes {
          containerTemplate {
            // TODO: Upload python testing container to GCR with 'make' installed
            image 'mindovermiles262/pytest:0.2.0'
            name 'dataeng-pytest'
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
  } // close STAGES
} // close PIPELINE