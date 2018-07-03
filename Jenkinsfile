#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
// For consistency with .ci/pipeline/release.groovy, we are not using alpine
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8" }
def static NODEJS_DOCKER_IMAGE() { return "node:8.11.2-alpine" }
def static ELASTICSEARCH_DOCKER_IMAGE() { return "docker.elastic.co/elasticsearch/elasticsearch-oss:6.2.4" }
def static ZEEBE_DOCKER_IMAGE() { return "camunda/zeebe:0.11.0" }
def static OPERATE_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-operate" }

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
  initContainers:
  - name: init-sysctl
    image: busybox
    imagePullPolicy: IfNotPresent
    command: ["sysctl", "-w", "vm.max_map_count=262144"]
    securityContext:
      privileged: true
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=\$(LIMITS_CPU)
    resources:
      limits:
        cpu: 2
        memory: 2Gi
      requests:
        cpu: 2
        memory: 2Gi
  - name: node
    image: ${NODEJS_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE()}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms1g -Xmx1g"
    - name: cluster.name
      value: docker-cluster
    - name: discovery.type
      value: single-node
    - name: action.auto_create_index
      value: false
    - name: bootstrap.memory_lock
      value: true
    securityContext:
      privileged: true
      capabilities:
        add:
          - IPC_LOCK
    ports:
    - containerPort: 9200
      name: es-http
      protocol: TCP
    - containerPort: 9300
      name: es-transport
      protocol: TCP
    resources:
      limits:
        cpu: 1
        memory: 3Gi
      requests:
        cpu: 1
        memory: 3Gi
  - name: zeebe
    image: ${ZEEBE_DOCKER_IMAGE()}
    env:
      - name: ZEEBE_HOST
        value: localhost
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -Xms512m
          -Xmx512m
    ports:
    - containerPort: 51015
      name: zeebe-broker
      protocol: TCP
    - containerPort: 51016
      name: zeebe-raft
      protocol: TCP
    - containerPort: 51017
      name: zeebe-gossip
      protocol: TCP
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
    stage('Build Frontend') {
      steps {
        container('node') {
          sh '''
            cd ./client
            yarn
            yarn build
          '''
        }
      }
    }
    stage('Build Backend') {
      steps {
        container('maven') {
          sh '''
            mvn clean install -P -docker,skipFrontendBuild -DskipTests=true -B -T$LIMITS_CPU --fail-at-end \
                -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
          '''
        }
      }
    }
    stage('Unit tests') {
      parallel {
        stage('Backend') {
          steps {
            container('maven') {
              sh '''
                cd ./backend
                mvn verify -P -docker -B -T$LIMITS_CPU --fail-at-end \
                    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
              '''
            }
          }
          post {
            always {
              junit testResults: 'backend/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
        stage('Frontend') {
          steps {
            container('node') {
              sh '''
                cd ./client
                yarn test:ci
              '''
            }
          }
          post {
            always {
              junit testResults: 'client/jest-test-results.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
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
