#!/usr/bin/env groovy

def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8" }

static String gcloudAgent() {
  return """
---
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: services
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  serviceAccountName: ci-operate-camunda-cloud
  containers:
    - name: gcloud
      image: google/cloud-sdk:alpine
      imagePullPolicy: Always
      command: ["cat"]
      tty: true
      resources:
        limits:
          cpu: 1
          memory: 512Mi
        requests:
          cpu: 200m
          memory: 128Mi
    - name: maven
      image: ${MAVEN_DOCKER_IMAGE()}
      command: ["cat"]
      tty: true
      env:
        # every JVM process will get a 1/4 of HEAP from total memory
        - name: JAVA_TOOL_OPTIONS
          value: |
            -XX:+UnlockExperimentalVMOptions
            -XX:+UseCGroupMemoryLimitForHeap
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
        - name: TZ
          value: Europe/Berlin
      resources:
        limits:
          cpu: 1
          memory: 1Gi
        requests:
          cpu: 1
          memory: 1Gi
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

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml gcloudAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 1, unit: 'HOURS')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-operate',
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
        container('gcloud') {
            sh ("""
                echo "Hello World"
            """)
        }
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
    // always {
    //   container('gcloud') {
    //       sh ("kubectl delete -f .ci/podSpecs/performanceTests/ns.yml")
    //   }
    // }
  }
}
