#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
def static DOCKER_IMAGE() { return "gcr.io/google.com/cloudsdktool/cloud-sdk:alpine" }

static String agent(env) {
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
    image: ${DOCKER_IMAGE()}
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
      label "zeebe-tasklist-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml agent(env)
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 15, unit: 'MINUTES')
  }

  environment {
      REGISTRY = credentials('docker-registry-ci3')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'https://github.com/camunda/infra-core',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: 'github-tasklist-app',
            poll: false

        container('gcloud') {
            camundaInstallKubectl()
            sh ("""
                # gcloud
                echo '${REGISTRY}' > account.json
                gcloud auth activate-service-account --key-file=account.json
                gcloud info
            """)
        }
      }
    }
    stage('Cleanup K8s branches') {
      steps {
        container('gcloud') {
          withCredentials([usernamePassword(credentialsId: 'github-tasklist-app', usernameVariable: 'GITHUB_APP', passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
            sh("""
              ./cmd/k8s/cleanup-branch-deployment \
              camunda/tasklist \
              zeebe-tasklist \
              gcr.io/ci-30-162810/tasklist \
              ${GITHUB_APP}:${GITHUB_ACCESS_TOKEN}
            """)
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

