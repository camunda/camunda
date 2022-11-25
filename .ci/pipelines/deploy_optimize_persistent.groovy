#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
// https://github.com/camunda/optimize-jenkins-shared-library
@Library(['camunda-ci', 'optimize-jenkins-shared-library']) _

def static NODE_POOL() { 'agents-n1-standard-8-netssd-preempt' }
def static GCLOUD_DOCKER_IMAGE() { 'gcr.io/google.com/cloudsdktool/cloud-sdk:alpine' }

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml plainGcloudAgent(NODE_POOL(), GCLOUD_DOCKER_IMAGE())
    }
  }

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
  }

  stages {
    stage('Deploy Persistent environment') {
      steps {
        build job: '/deploy-branch-to-k8s-gha',
          parameters: [
          string(name: 'BRANCH', value: "persistent"),
          string(name: 'DOCKER_TAG', value: params.OPTIMIZE_VERSION),
          string(name: 'CAMBPM_VERSION', value: params.CAMBPM_VERSION),
          string(name: 'ES_VERSION', value: params.ES_VERSION),
          string(name: 'REF', value: "master"),
           ]
        }
    }
  }

  post {
    changed {
      sendEmailNotification()
    }
    always {
      retriggerBuildIfDisconnected()
    }
  }
}
