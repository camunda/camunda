#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8-alpine" }
def static NODEJS_DOCKER_IMAGE() { return "node:8.11.2-alpine" }
def static DIND_DOCKER_IMAGE() { return "docker:18.03.1-ce-dind" }
def static OPTIMIZE_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-operate" }

String getGitCommitMsg() {
  return sh(script: 'git log --format=%B -n 1 HEAD', returnStdout: true).trim()
}

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${env.BUILD_URL}consoleFull"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

static String mavenNodeJSAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=1
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
  - name: node
    image: ${NODEJS_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
  - name: docker
    image: ${DIND_DOCKER_IMAGE()}
    args: ["--storage-driver=overlay2"]
    securityContext:
      privileged: true
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
"""
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenNodeJSAgent(env)
    }
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(daysToKeepStr:'14', numToKeepStr:'50'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Frontend') {
      steps {
        container('node') {
          sh '''
            cd ./client
            yarn
            yarn build
            CI=true yarn test
          '''
        }
      }
    }
    stage('Backend') {
      steps {
        container('maven') {
          sh '''
            cd ./backend
            mvn clean install -B
          '''
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
