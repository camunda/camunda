#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
def static KUBECTL_DOCKER_IMAGE() { return "lachlanevenson/k8s-kubectl:latest" }

static String kubectlAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  serviceAccountName: ci-optimize-camunda-cloud
  containers:
  - name: kubectl
    image: ${KUBECTL_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 500Mi
      requests:
        cpu: 1
        memory: 500Mi
"""
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  String buildResultUrl = "${env.BUILD_URL}"
  if(env.RUN_DISPLAY_URL) {
    buildResultUrl = "${env.RUN_DISPLAY_URL}"
  }

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${buildResultUrl}"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml kubectlAgent(env)
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 15, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda-ci/k8s-infrastructure',
            branch: 'master',
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
      }
    }
    stage('Deploy to K8s') {
      steps {
        sh ('''
          ./cmd/k8s/deploy-template-to-branch.sh \
              ${BRANCH} \
              optimize_${BRANCH} \
              gcr.io/ci-30-162810/camunda-optimize \
              infrastructure/ci-30-162810/deployments/optimize_branch \
        ''')
      }
      post {
        always {
          buildNotification(currentBuild.result)
        }
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
  }
}
