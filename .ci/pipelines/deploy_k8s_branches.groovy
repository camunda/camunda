#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
def static GCLOUD_DOCKER_IMAGE() { return "gcr.io/google.com/cloudsdktool/cloud-sdk:alpine" }

static String kubectlAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: zeebe-tasklist-ci-build
    camunda.cloud/source: jenkins
    camunda.cloud/managed-by: "${env.JENKINS_DOMAIN}"
  annotations:
    camunda.cloud/created-by: "${env.BUILD_URL}"
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  serviceAccountName: ci-zeebe-tasklist-camunda-cloud
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
      cloud 'zeebe-tasklist-ci'
      label "zeebe-tasklist-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml kubectlAgent(env)
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '100'))
    timestamps()
    timeout(time: 15, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        dir('infra-core') {
          git url: 'https://github.com/camunda/infra-core',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: 'github-tasklist-app',
            poll: false
        }
        dir('tasklist') {
          git url: 'https://github.com/camunda-cloud/tasklist.git',
            branch: "${params.ZEEBE_TASKLIST_BRANCH}",
            credentialsId: 'github-cloud-zeebe-tasklist-app',
            poll: false
        }

        container('gcloud') {
          camundaInstallKubectl()
        }
      }
    }
    stage('Deploy to K8s') {
      steps {
        container('gcloud') {
          dir('infra-core') {
            sh("""
              ./cmd/k8s/deploy-template-to-branch \
              ${WORKSPACE}/infra-core/camunda-ci/deployments/zeebe-tasklist-branch \
              ${WORKSPACE}/tasklist/.ci/branch-deployment \
              ${params.BRANCH} \
              zeebe-tasklist
            """)
          }
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'infra-core/rendered-templates/**/*'
        }
      }
    }
    stage('Update URLs in GitHub Status') {
      steps {
        script {
          dir('tasklist') {
            SHA = org.camunda.helper.GitUtilities.getGitSha(this)
          }
        }
        script {
          dir('infra-core') {
            hosts = sh(script: 'cat hosts.txt', returnStdout: true).trim()
            def arr = hosts.split()
            arr.each { host -> 
              withCredentials([usernamePassword(credentialsId: 'github-cloud-zeebe-tasklist-app', usernameVariable: 'GITHUB_APP', passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
                org.camunda.helper.GitHubAPI.postCommitStatus("${GITHUB_ACCESS_TOKEN}", 'camunda-cloud', 'tasklist', "${SHA}", "${host}", 'success', "https://${host}", 'deployment url')
              }
            }
          }
        }
      }
    }
  }

  post {
    failure {
      script {
        def notification = load "${pwd()}/.ci/pipelines/build_notification.groovy"
        notification.buildNotification(currentBuild.result)
      }
    }
  }
}
