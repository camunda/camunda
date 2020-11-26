#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
def static OPENJDK_MAVEN_DOCKER_IMAGE(String javaVersion) { return "maven:3.6.3-jdk-${javaVersion}" }
def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}" }
def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) { return "docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}" }


ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"


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
        value: 2
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 6
        memory: 6Gi
      requests:
        cpu: 6
        memory: 6Gi
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: JAVA_OPTS
        value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 2Gi
      requests:
        cpu: 4
        memory: 2Gi
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
      limits:
        cpu: 2
        memory: 2Gi
      requests:
        cpu: 2
        memory: 2Gi
"""
}

static String mavenAgent(javaVersion) {
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
  containers:
  - name: maven
    image: ${OPENJDK_MAVEN_DOCKER_IMAGE(javaVersion)}
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
  if (env.RUN_DISPLAY_URL) {
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

void integrationTestSteps() {
  cloneGitRepo()
  container('maven') {
    runMaven("test -Dskip.fe.build");
    runMaven("verify -Dskip.docker -Pit,engine-latest -pl backend,upgrade,util/optimize-reimport-preparation -am")
  }
}

private void cloneGitRepo() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
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
          yaml mavenAgent("8-slim")
        }
      }
      steps {
        cloneGitRepo()
        script {
          def mavenProps = readMavenPom().getProperties()
          env.ES_VERSION = params.ES_VERSION ?: mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
          env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
        }
      }
    }
    stage('Java Integration Tests') {
      failFast false
      parallel {
        stage("OpenJDK 8 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-JDK8_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent(OPENJDK_MAVEN_DOCKER_IMAGE("8-slim"), "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("OpenJDK 11 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-JDK11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent(OPENJDK_MAVEN_DOCKER_IMAGE("11-slim"), "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("OpenJDK 13 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-JDK13_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent(OPENJDK_MAVEN_DOCKER_IMAGE("13"), "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("Adopt Open JDK 8 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-ADOPT8_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent("adoptopenjdk/maven-openjdk8:latest", "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("Adopt Open JDK 11 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-ADOPT11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent("adoptopenjdk/maven-openjdk11:latest", "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage("Adopt Open JDK 13 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-ADOPT13_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent("adoptopenjdk/maven-openjdk13:latest", "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
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
