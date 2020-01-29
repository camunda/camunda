#!/usr/bin/env groovy

boolean slaveDisconnected() {
  return currentBuild.rawBuild.getLog(10000).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException).*/
}

// general properties for CI execution
def static NODE_POOL() { return "slaves-ssd" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }
def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "camunda/camunda-bpm-platform:${cambpmVersion}" }
def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) { return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
PREV_ES_TEST_VERSION_POM_PROPERTY = "previous.optimize.elasticsearch.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

static String mavenElasticsearchAgent(env, prevEsVersion, esVersion, cambpmVersion) {
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
  securityContext:
    fsGroup: 1000
  volumes:
    - name: es-storage
      hostPath:
        path: /mnt/disks/array0
        type: Directory
    - name: cambpm-storage
      emptyDir: {}
    - name: es-snapshot
      emptyDir: {}
  initContainers:
  - name: init-sysctl
    image: ${MAVEN_DOCKER_IMAGE()}
    command:
    - sysctl
    - -w
    - vm.max_map_count=262144
    securityContext:
      privileged: true
  - name: cleanup
    image: busybox
    imagePullPolicy: Always
    command: ["rm", "-fr", "/data/*"]
    volumeMounts:
      - name: es-storage
        mountPath: /data
        subPath: data
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
    volumeMounts:
      - name: es-storage
        mountPath: /es-storage
      - name: cambpm-storage
        mountPath: /cambpm-storage
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: DB_DRIVER
        value: org.postgresql.Driver
      - name: DB_USERNAME
        value: camunda
      - name: DB_PASSWORD
        value: camunda123
      - name: DB_URL
        value: jdbc:postgresql://opt-ci-perf.db:5432/optimize-ci-performance
      - name: TZ
        value: Europe/Berlin
      - name: WAIT_FOR
        value: opt-ci-perf.db:5432
      - name: JAVA_OPTS
        value: "-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m"
    resources:
      limits:
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 4
        memory: 3Gi
    volumeMounts:
      - name: cambpm-storage
        mountPath: /camunda/logs
  - name: elasticsearch-old
    image: ${ELASTICSEARCH_DOCKER_IMAGE(prevEsVersion)}
    env:
    - name: ES_NODE_NAME
      valueFrom:
        fieldRef:
          fieldPath: metadata.name
    - name: ES_JAVA_OPTS
      value: "-Xms5g -Xmx5g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: action.auto_create_index
      value: false
    - name: bootstrap.memory_lock
      value: true
    - name: http.port
      value: 9250
    - name: path.repo
      value: /var/lib/elasticsearch/snapshots
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    ports:
    - containerPort: 9250
      name: es-http
      protocol: TCP
    resources:
      limits:
        cpu: 12
        memory: 6Gi
      requests:
        cpu: 12
        memory: 6Gi
    volumeMounts:
      - name: es-storage
        mountPath: /usr/share/elasticsearch/data
        subPath: data-old
      - name: es-storage
        mountPath: /usr/share/elasticsearch/logs
        subPath: logs-old
      - name: es-snapshot
        mountPath: /var/lib/elasticsearch/snapshots
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_NODE_NAME
      valueFrom:
        fieldRef:
          fieldPath: metadata.name
    - name: ES_JAVA_OPTS
      value: "-Xms1g -Xmx1g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: action.auto_create_index
      value: false
    - name: bootstrap.memory_lock
      value: true
    - name: http.port
      value: 9200
    - name: path.repo
      value: /var/lib/elasticsearch/snapshots
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    ports:
    - containerPort: 9200
      name: es-http
      protocol: TCP
    resources:
      limits:
        cpu: 2
        memory: 2Gi
      requests:
        cpu: 2
        memory: 2Gi
    volumeMounts:
      - name: es-storage
        mountPath: /usr/share/elasticsearch/data
        subPath: data-new
      - name: es-storage
        mountPath: /usr/share/elasticsearch/logs
        subPath: logs-new
      - name: es-snapshot
        mountPath: /var/lib/elasticsearch/snapshots
"""
}

static String mavenAgent() {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  tolerations:
    - key: "slaves"
      operator: "Exists"
      effect: "NoSchedule"
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
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
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

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '50'))
    timestamps()
    timeout(time: 100, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        cloneGitRepo()
        script {
          def mavenProps = readMavenPom().getProperties()
          env.ES_VERSION = params.ES_VERSION ?: mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
          env.PREV_ES_VERSION = params.PREV_ES_VERSION ?: mavenProps.getProperty(PREV_ES_TEST_VERSION_POM_PROPERTY)
          env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
        }
      }
    }
    stage('Performance') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml mavenElasticsearchAgent(env, env.PREV_ES_VERSION, env.ES_VERSION, env.CAMBPM_VERSION)
        }
      }
      steps {
        cloneGitRepo()
        container('maven') {
          sh 'apt-get update && apt-get install jq netcat -y'
          runMaven('-T\$LIMITS_CPU -DskipTests -Dskip.fe.build -Dskip.docker clean install')
          runMaven('-Dupgrade.timeout.seconds=${UPGRADE_TIMEOUT} -Delasticsearch.snapshot.path=/var/lib/elasticsearch/snapshots -Dskip.docker -Pstatic-data-upgrade-es-schema-tests -pl qa/upgrade-es-schema-tests clean verify')
        }
      }
      post {
        always {
          container('maven') {
            sh 'curl localhost:9250/_cat/indices?v'
            sh ('''#!/bin/bash -ex
              cp -R --parents /es-storage/logs-old /es-storage/logs-new /cambpm-storage .
              chown -R 10000:1000 ./{es-storage,cambpm-storage}
            ''')
            archiveArtifacts artifacts: 'es-storage/logs-*/*,cambpm-storage/**/*', onlyIfSuccessful: false
          }
        }
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
    always {
      // Retrigger the build if the slave disconnected
      script {
        if (slaveDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

private void cloneGitRepo() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
}
