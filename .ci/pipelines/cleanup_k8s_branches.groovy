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
    agent: operate-ci-build
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
  serviceAccountName: ci-operate-camunda-cloud
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
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
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
            credentialsId: 'github-operate-app',
            poll: false

        container('gcloud') {
            camundaInstallKubectl()
            sh ("""
                # gcloud
                echo '${REGISTRY}' > account.json
                gcloud auth activate-service-account --key-file=account.json
                gcloud info

                # setup ssh for github clone
                mkdir -p ~/.ssh
                ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
            """)
        }
      }
    }
    stage('Cleanup K8s branches') {
      steps {
        container('gcloud') {
          withCredentials([usernamePassword(credentialsId: 'github-operate-app', usernameVariable: 'GITHUB_APP', passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
            sh("""
              ./cmd/k8s/cleanup-branch-deployment \
              camunda/operate \
              operate \
              gcr.io/ci-30-162810/camunda-operate \
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

