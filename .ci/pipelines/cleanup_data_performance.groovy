#!/usr/bin/env groovy
def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

static String gcloudAgent() {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: services
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  serviceAccountName: ci-optimize-camunda-cloud
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
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 2
        memory: 2Gi
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
  sh ("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml gcloudAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "${env.JOB_BASE_NAME}-${env.BUILD_ID}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '50'))
    timestamps()
    timeout(time: 10, unit: 'HOURS')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize',
                branch: "${params.BRANCH}",
                credentialsId: 'camunda-jenkins-github-ssh',
                poll: false
        container('gcloud') {
          sh ("""
                # install jq
                apk add --no-cache jq gettext
                # kubectl
                gcloud components install kubectl --quiet
                
                bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${REGISTRY_USR}" "${REGISTRY_PSW}" "${SQL_DUMP}"
            """)
        }
      }
    }
    stage('Import') {
      steps {
        container('gcloud') {
          sh ("""
                bash .ci/podSpecs/performanceTests/wait-for-import-to-finish.sh "${NAMESPACE}"
                bash .ci/podSpecs/performanceTests/scale-optimize-down.sh "${NAMESPACE}"
            """)
        }
      }
    }
    stage('CleanupPerformanceTest') {
      steps {
        container('maven') {
          runMaven('-T\$LIMITS_CPU -DskipTests -Dskip.fe.build -Dskip.docker clean install')
          runMaven("-Pcleanup-performance-test -f qa/cleanup-performance-tests/pom.xml test -Ddb.url=jdbc:postgresql://postgres.${NAMESPACE}:5432/engine -Dengine.url=http://cambpm.${NAMESPACE}:8080/engine-rest -Des.host=elasticsearch.${NAMESPACE} -Dcleanup.timeout.minutes=${CLEANUP_TIMEOUT_MINUTES}")
        }
      }
      post {
        always {
          container('maven') {
            sh "curl -s elasticsearch.${NAMESPACE}:9200/_cat/indices?v || true"
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
      container('gcloud') {
        sh ("bash .ci/podSpecs/performanceTests/kill.sh \"${NAMESPACE}\"")
      }
    }
  }
}
