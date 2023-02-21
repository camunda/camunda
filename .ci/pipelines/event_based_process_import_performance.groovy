#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }

def static NODE_POOL() { return "agents-n1-standard-8-netssd-stable" }

static String gCloudAndMavenAgent() {
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
  containers:
  - name: gcloud
    image: gcr.io/google.com/cloudsdktool/cloud-sdk:alpine
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

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      serviceAccount 'ci-optimize-camunda-cloud'
      yaml gCloudAndMavenAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "optimize-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
    EVENT_IMPORT_ENABLED = true
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
          optimizeCloneGitRepo(params.BRANCH)
          setBuildEnvVars()
        }
      }
    }
    stage('Prepare') {
      steps {
        container('gcloud') {
            sh 'apk add --no-cache jq gettext'
            camundaInstallKubectl()
            sh ("""
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
                # Asserting greater or equal as on pod failure ingest may get reingested which is supported by optimize
                # as deduplication happens on processing the event log
                test "\$NUMBER_OF_EVENTS" -ge "\${EXTERNAL_EVENT_COUNT}" || error=true

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

                # sleep for one more minute to avoid a race condition if the last page of the publish did not include
                # all events of the last batch (can be the case if import batch size < ingest batch size)
                sleep 1m

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
      sendEmailNotification()
    }
    always {
      container('gcloud') {
          sh ('''#!/bin/bash -ex
                 kubectl -n \${NAMESPACE} logs elasticsearch-0 -c elasticsearch > elasticsearch.log
                 ''')
          archiveArtifacts artifacts: 'elasticsearch.log', onlyIfSuccessful: false
          sh ("bash .ci/podSpecs/performanceTests/kill.sh \"${NAMESPACE}\"")
      }
      retriggerBuildIfDisconnected()
    }
  }
}

