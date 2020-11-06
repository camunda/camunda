#!/usr/bin/env groovy

/******** START PIPELINE *******/

pipeline {
  agent {
    label 'master'
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 120, unit: 'MINUTES')
  }

  stages {
    stage('Firefox') {
      steps {
        retry(3) {
          build job: 'browserstack/test_e2e_browserstack', parameters: [
            string(name: 'OPERATE_BROWSERSTACK_BROWSER', value: 'browserstack:Firefox'),
            string(name: 'OPERATE_BRANCH', value: params.OPERATE_BRANCH),
          ]
        }
      }
    }
    stage('Edge') {
      steps {
        retry(3) {
          build job: 'browserstack/test_e2e_browserstack', parameters: [
            string(name: 'OPERATE_BROWSERSTACK_BROWSER', value: 'browserstack:Edge'),
            string(name: 'OPERATE_BRANCH', value: params.OPERATE_BRANCH),
          ]
        }
      }
    }
  }
  post {
    failure {
      script {
        slackSend(
          channel: "#operate-ci",
          message: "Browserstack E2E Tests failed! ${currentBuild.absoluteUrl}")
      }
    }
  }
}
