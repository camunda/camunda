#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
// https://github.com/camunda/optimize-jenkins-shared-library
@Library(['camunda-ci', 'optimize-jenkins-shared-library']) _

def static NODE_POOL() { 'agents-n1-standard-32-netssd-preempt' }
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

  parameters {
    string defaultValue: 'master', description: 'Optimize branch for deployment', name: 'BRANCH'
    booleanParam defaultValue: false, description: 'Deploy Optimize persistent ElasticSearch', name: 'DEPLOY_ELASTICSEARCH'
    booleanParam defaultValue: false, description: 'Deploy Optimize persistent App', name: 'DEPLOY_OPTIMIZE'
  }

  stages {
    stage('Prepare') {
      when {
        anyOf {
          expression { params.DEPLOY_ELASTICSEARCH == true }
          expression { params.DEPLOY_OPTIMIZE == true }
        }
      }
      steps {
        dir('optimize') {
          optimizeCloneGitRepo(params.BRANCH)
        }
        container('gcloud') {
          sh '''
            gcloud components install kubectl --quiet
          '''
        }
      }
    }

    stage('Deploy ElasticSearch') {
      when {
        expression { params.DEPLOY_ELASTICSEARCH == true }
      }
      steps {
        container('gcloud') {
          dir('optimize') {
            sh '''
                kubectl apply -f .ci/persistent-deployment/elasticsearch
            '''
          }
        }
      }
    }

    stage('Deploy Optimize') {
      when {
        expression { params.DEPLOY_OPTIMIZE == true }
      }
      steps {
        container('gcloud') {
          dir('optimize') {
            sh '''
                kubectl apply -f .ci/persistent-deployment/optimize
            '''
          }
        }
      }
    }
  }

  post {
    changed {
      sendNotification(currentBuild.result,null,null,[[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])
    }
    always {
      // Retrigger the build if the agent node disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}
