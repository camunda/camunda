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
    cloud.google.com/gke-nodepool: services
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
            env.EVENT_IMPORT_ENABLED = true
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

                bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${SQL_DUMP}" "${ES_VERSION}" "${CAMBPM_VERSION}" "${ES_REFRESH_INTERVAL}" "${EVENT_IMPORT_ENABLED}" "${ES_NUM_NODES}"
            """)
        }
      }
    }
    stage('ImportTest') {
      steps {
        container('gcloud') {
          sh ("""
                bash .ci/podSpecs/performanceTests/wait-for-import-to-finish.sh "${NAMESPACE}"

                curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/_refresh'

                # assert expected counts
                # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                NUMBER_OF_ACTIVITY_EVENTS=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-camunda-activity-*/_count' | jq '.count') || true
                NUMBER_OF_VARIABLE_UPDATES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-variable-update-instance*/_count' | jq '.count') || true

                # note: each call here is followed by `|| error=true` to not let the whole script fail if one assert fails
                # a final if block checks if there was an error and will let the script fail
                export EXPECTED_NUMBER_OF_VARIABLES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_variables | cut -f2 -d':'`
                export EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_activity_instances | cut -f2 -d':'`

                echo "NUMBER_OF_ACTIVITY_EVENTS"
                # we use -ge here as the event index is a forward written log, and entities of the last timestamp might get duplicated
                test "\$NUMBER_OF_ACTIVITY_EVENTS" -ge "\${EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES}" || error=true
                echo "NUMBER_OF_VARIABLE_UPDATES"
                # we use -ge here as the variable update index contains entries for each variable update
                test "\$NUMBER_OF_VARIABLE_UPDATES" -ge "\${EXPECTED_NUMBER_OF_VARIABLES}" || error=true

                #Fail the build if there was an error
                if [ \$error ]
                then
                  exit -1
                fi
            """)
        }
      }
    }
    stage('CamundaActivityEventProcessPublish') {
      steps {
        container('gcloud') {
          sh ("""
                # make sure that on piping we fail if any command of the pipe failed
                set -o pipefail

                bash .ci/pipelines/event_import_performance/create-and-publish-event-process.sh ${NAMESPACE} .ci/pipelines/event_import_performance/invoiceEventProcessMapping.json

                curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/_refresh'
                # assert expected counts
                NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-event-process-instance-*/_count' | jq '.count') || true
                EXPECTED_NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES=\$(curl -s -H 'Content-Type: application/json' -XPOST 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_doc/_count' -d '{"query":{"bool":{"must":[{"term":{"processDefinitionKey":"invoice"}}],"must_not":[],"should":[]}}}' | jq '.count') || true

                # note: each call here is followed by `|| error=true` to not let the whole script fail if one assert fails
                # a final if block checks if there was an error and will let the script fail
                echo "NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES"
                test "\$NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES" = "\${EXPECTED_NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES}" || error=true

                curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true

                # Fail the build if there was an error
                if [ \$error ]
                then
                  exit -1
                fi

                # store the number of activity event processes for reuse
                echo "\$NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES" > numberOfActivityEventProcessInstances.txt
            """)
        }
      }
    }
    stage('ExternalEventIngestion') {
      steps {
        container('gcloud') {
          sh ("""
                # make sure that on piping we fail if any command of the pipe failed
                set -o pipefail

                # ingest events
                bash .ci/pipelines/event_import_performance/ingestEvents.sh ${NAMESPACE} ${EXTERNAL_EVENT_COUNT} secret

                curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/_refresh'
                # assert expected counts
                # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                NUMBER_OF_EVENTS=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-event/_count' | jq '.count') || true

                echo "\$NUMBER_OF_EVENTS"
                test "\$NUMBER_OF_EVENTS" = "\${EXTERNAL_EVENT_COUNT}" || error=true

                curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true

                #Fail the build if there was an error
                if [ \$error ]
                then
                  exit -1
                fi
            """)
        }
      }
    }
    stage('ExternalEventProcessPublish') {
      steps {
        container('gcloud') {
          sh ("""
                # make sure that on piping we fail if any command of the pipe failed
                set -o pipefail

                bash .ci/pipelines/event_import_performance/create-and-publish-event-process.sh ${NAMESPACE} .ci/pipelines/event_import_performance/externalInvoiceEventProcessMapping.json

                curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/_refresh'
                # assert expected counts
                # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                NUMBER_OF_EVENT_PROCESS_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-event-process-instance-*/_count' | jq '.count') || true
                # this reads the previously saved count of activity event processInstances
                NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES=\$(cat numberOfActivityEventProcessInstances.txt)
                EXPECTED_NUMBER_OF_EVENT_PROCESS_INSTANCES=\$((NUMBER_OF_ACTIVITY_EVENT_PROCESS_INSTANCES+${EXTERNAL_EVENT_COUNT}/4))

                echo "NUMBER_OF_EVENT_PROCESS_INSTANCES"
                test "\$NUMBER_OF_EVENT_PROCESS_INSTANCES" = "\$EXPECTED_NUMBER_OF_EVENT_PROCESS_INSTANCES" || error=true

                curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true

                #Fail the build if there was an error
                if [ \$error ]
                then
                  exit -1
                fi
            """)
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
