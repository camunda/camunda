#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
def static GCLOUD_DOCKER_IMAGE() { return "google/cloud-sdk:alpine" }

static String kubectlAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  serviceAccountName: ci-operate-camunda-cloud
  containers:
  - name: gcloud
    image: ${GCLOUD_DOCKER_IMAGE()}
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 500Mi
      requests:
        cpu: 500m
        memory: 500Mi
"""
}

pipeline {

  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").take(20)}-${env.BUILD_ID}"
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
        dir('k8s-infrastructure') {
          git url: 'git@github.com:camunda-ci/k8s-infrastructure',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
        }
        dir('operate') {
          git url: 'git@github.com:camunda/camunda-operate',
            branch: "${params.OPERATE_BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
        }

        container('gcloud') {
          sh("""
            gcloud components install kubectl --quiet
          """)
        }
      }
    }
    stage('Deploy to K8s') {
      steps {
        container('gcloud') {
          dir('k8s-infrastructure') {
            sh("""
              ./cmd/k8s/deploy-template-to-branch \
              ${WORKSPACE}/k8s-infrastructure/infrastructure/ci-30-162810/deployments/operate-branch \
              ${WORKSPACE}/operate/.ci/branch-deployment \
              ${params.BRANCH} \
              operate
            """)
          }
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'k8s-infrastructure/rendered-templates/**/*'
        }
      }
    }
  }

  post {
    failure {
      def notification = load "build_notification.groovy"
      notification.buildNotification(currentBuild.result)
    }
  }
}
