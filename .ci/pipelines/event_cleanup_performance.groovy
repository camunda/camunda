#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.3-jdk-8-slim" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

static String gCloudAndMavenAgent() {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-8-netssd-stable
  imagePullSecrets:
    - name: registry-camunda-cloud
  serviceAccountName: ci-optimize-camunda-cloud
  containers:
  - name: gcloud
    image: google/cloud-sdk:alpine
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 2
        memory: 1Gi
      requests:
        cpu: 2
        memory: 1Gi
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
      yaml gCloudAndMavenAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "${env.JOB_BASE_NAME}-${env.BUILD_ID}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 24, unit: 'HOURS')
  }

  stages {
    stage('Retrieve CamBPM and Elasticsearch version') {
      steps {
        container('maven') {
          cloneGitRepo()
          script {
            def mavenProps = readMavenPom().getProperties()
            env.ES_VERSION = params.ES_VERSION ?: mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
            env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
          }
        }
      }
    }
    stage('Prepare') {
      steps {
        container('gcloud') {
          sh ("""
                # install jq
                apk add --no-cache jq gettext
                # kubectl
                gcloud components install kubectl --quiet

                bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${SQL_DUMP}" "${ES_VERSION}" "${CAMBPM_VERSION}" "30s" "true" "${ES_NUM_NODES}"
            """)
        }
      }
    }
    stage('ExternalEventIngestion') {
      steps {
        container('gcloud') {
          sh("""
                # make sure that on piping we fail if any command of the pipe failed
                set -o pipefail

                # ingest events
                bash .ci/pipelines/event_import_performance/ingestEvents.sh ${NAMESPACE} ${EXTERNAL_EVENT_COUNT} secret
            """)
        }
      }
    }
    stage('Import') {
      steps {
        container('gcloud') {
          sh ("""
                bash .ci/podSpecs/performanceTests/wait-for-import-to-finish.sh "${NAMESPACE}"
            """)
        }
      }
    }
    stage('EventProcessPublish') {
      steps {
        container('gcloud') {
          sh ("""
                # make sure that on piping we fail if any command of the pipe failed
                set -o pipefail

                bash .ci/pipelines/event_import_performance/create-and-publish-event-process.sh ${NAMESPACE} .ci/pipelines/event_import_performance/invoiceEventProcessMapping.json
                bash .ci/pipelines/event_import_performance/create-and-publish-event-process.sh ${NAMESPACE} .ci/pipelines/event_import_performance/externalInvoiceEventProcessMapping.json
                curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true

                bash .ci/podSpecs/performanceTests/scale-optimize-down.sh "${NAMESPACE}"
            """)
        }
      }
    }
    stage('CleanupPerformanceTest') {
      steps {
        container('maven') {
          runMaven('-T\$LIMITS_CPU -pl backend -am -DskipTests -Dskip.fe.build -Dskip.docker clean install')
          runMaven("-Pevent-cleanup-performance -f qa/cleanup-performance-tests/pom.xml test -Ddb.url=jdbc:postgresql://postgres.${NAMESPACE}:5432/engine -Dengine.url=http://cambpm.${NAMESPACE}:8080/engine-rest -Des.host=elasticsearch.${NAMESPACE} -Dcleanup.timeout.minutes=${CLEANUP_TIMEOUT_MINUTES}")
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
      // Retrigger the build if the slave disconnected
      script {
        if (agentDisconnected()) {
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
