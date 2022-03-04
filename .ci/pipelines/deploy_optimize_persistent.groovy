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
          camundaInstallKubectl()
          camundaInstallKustomize()
        }
      }
    }

    stage('Deploy ElasticSearch') {
      when {
        expression { params.DEPLOY_ELASTICSEARCH == true }
      }
      steps {
        container('gcloud') {
          dir('optimize/.ci/persistent-deployment/elasticsearch') {
            deploy()
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
          dir('optimize/.ci/persistent-deployment/optimize') {
            deploy()
          }
        }
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

def deploy() {
  sh """
    kustomize cfg set . source jenkins
    kustomize cfg set . managed-by ${JENKINS_DOMAIN}
    kustomize cfg set . created-by ${BUILD_URL}
    kubectl apply -k .
  """
}
