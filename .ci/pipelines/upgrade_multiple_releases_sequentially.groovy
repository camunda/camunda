#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }
def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "registry.camunda.cloud/camunda-bpm-platform-ee:${cambpmVersion}" }
def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) { return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}" }

static String mavenUpgradeTestAgent(esVersion = "6.2.0", cambpmVersion = "7.11.0") {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-cambpm-config
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
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
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/bpm-platform.xml
      subPath: bpm-platform.xml
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms1g -Xmx1g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: bootstrap.memory_lock
      value: true
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      requests:
        cpu: 1
        memory: 2Gi
"""
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  String buildResultUrl = "${env.BUILD_URL}"
  if (env.RUN_DISPLAY_URL) {
    buildResultUrl = "${env.RUN_DISPLAY_URL}"
  }

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${buildResultUrl}"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

void runMaven(String cmd) {
  sh("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}

void upgradeTestSteps(String startVersion, String targetVersion) {
  container('maven') {
    def versionList = generateUpgradeList(startVersion, targetVersion)
    runMaven("install -Dskip.docker -DskipTests -pl backend -am -Pproduction,engine-7.10")
    runMaven("install -Dskip.docker -DskipTests -f qa -Pproduction,engine-7.10")
    runMaven("verify -Dskip.docker -pl qa/upgrade-es-schema-tests -Pengine-7.10,upgrade-es-schema-tests -Dupgrade.versions=${versionList}")
  }
}

static String generateUpgradeList(String startVersion, String targetVersion) {
  def tokenizedStartVersion = startVersion.tokenize('.')
  def startMajorVersion = tokenizedStartVersion[0] as Integer
  def startMinorVersion = tokenizedStartVersion[1] as Integer

  def tokenizedTargetVersion = targetVersion.tokenize('.')
  def targetMajorVersion = tokenizedTargetVersion[0] as Integer
  def targetMinorVersion = tokenizedTargetVersion[1] as Integer

  def versionList = []
  for (def majorVersion = startMajorVersion; majorVersion <= targetMajorVersion; majorVersion++) {
    for (def minorVersion = startMinorVersion; minorVersion < targetMinorVersion; minorVersion++) {
      versionList.add("$majorVersion.$minorVersion.0")
    }
  }

  versionList.add(targetVersion)

  return versionList.join(",")
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-seq-upgrade-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenUpgradeTestAgent("${params.ES_VERSION}", "${params.CAMBPM_VERSION}")
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize',
                branch: "${params.BRANCH}",
                credentialsId: 'camunda-jenkins-github-ssh',
                poll: false
      }
    }
    stage('Install required packages') {
      steps {
        container('maven') {
          sh 'apt-get update && apt-get install jq netcat -y'
        }
      }
    }
    stage('Sequential Upgrade Test') {
      environment {
        VERSION = readMavenPom().getVersion()
      }
      steps {
        upgradeTestSteps("${params.START_VERSION}", "${VERSION}")
      }
      post {
        always {
          junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
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
