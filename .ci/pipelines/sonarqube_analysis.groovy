#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-preempt" }

def static OPENJDK_MAVEN_DOCKER_IMAGE(String javaVersion) { return "maven:3.6.3-jdk-${javaVersion}" }

def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}"
}

def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) {
  return "docker.elastic.co/elasticsearch/elasticsearch:${esVersion}"
}

static String mavenIntegrationTestAgent(mavenImage, esVersion, cambpmVersion) {
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
    - name: registry-camunda-cloud
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci-v2/deployments/optimize
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
    image: ${mavenImage}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 4
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 8
        memory: 8Gi
      requests:
        cpu: 8
        memory: 8Gi
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: JAVA_OPTS
        value: "-Xms3g -Xmx3g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 6
        memory: 4Gi
      requests:
        cpu: 6
        memory: 4Gi
    volumeMounts:
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
      value: "-Xms4g -Xmx4g"
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
      limits:
        cpu: 8
        memory: 8Gi
      requests:
        cpu: 8
        memory: 8Gi
"""
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

void integrationTestSteps() {
  optimizeCloneGitRepo(params.BRANCH)
  container('maven') {
    runMaven("verify sonar:sonar -Dskip.unit.tests=false -Dskip.docker -Pit,coverage -pl backend,upgrade,util/optimize-reimport-preparation,test-coverage -am -Dsonar.projectKey=camunda_camunda-optimize -Dsonar.login=${SONARCLOUD_TOKEN}")
  }
}


pipeline {
  agent none
  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml plainMavenAgent(NODE_POOL(), OPENJDK_MAVEN_DOCKER_IMAGE("8-slim"))
        }
      }
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage("Sonar Analysis") {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-sonarqube_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml mavenIntegrationTestAgent(OPENJDK_MAVEN_DOCKER_IMAGE("11-slim"), "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
        }
      }
      environment {
        SONARCLOUD_TOKEN = credentials('sonarcloud-token')
      }
      steps {
        integrationTestSteps()
      }
    }
  }

  post {
    changed {
      sendNotification(currentBuild.result,null,null,[[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])
    }
    always {
      // Retrigger the build if the slave disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}
