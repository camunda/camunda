#!/usr/bin/env groovy
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8-slim" }

static String gcloudAgent() {
  return """
---
apiVersion: v1
kind: Pod
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
    stage('ImportTest') {
      steps {
        container('gcloud') {
          sh ("""
                bash .ci/podSpecs/performanceTests/wait-for-import-to-finish.sh "${NAMESPACE}"
                
                # assert expected counts
                # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                NUMBER_OF_PROCESS_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search?size=0' | jq '.hits.total') || true
                NUMBER_OF_ACTIVITY_INSTANCES=\$(curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"events": {"nested": {"path": "events"},"aggs": {"event_count": {"value_count": {"field": "events.id"}}}}}}' | jq '.aggregations.events.doc_count') || true
                NUMBER_OF_USER_TASKS=\$(curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"userTasks": {"nested": {"path": "userTasks"},"aggs": {"user_task_Count": {"value_count": {"field": "userTasks.id"}}}}}}' | jq '.aggregations.userTasks.doc_count') || true
                NUMBER_OF_VARIABLES=\$(curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{ "size": 0, "aggs": { "stringVariables": { "nested": { "path": "stringVariables" }, "aggs": { "variable_count": { "value_count": { "field": "stringVariables.id" } } } }, "integerVariables": { "nested": { "path": "integerVariables" }, "aggs": { "variable_count": { "value_count": { "field": "integerVariables.id" } } } }, "longVariables": { "nested": { "path": "longVariables" }, "aggs": { "variable_count": { "value_count": { "field": "longVariables.id" } } } }, "shortVariables": { "nested": { "path": "shortVariables" }, "aggs": { "variable_count": { "value_count": { "field": "shortVariables.id" } } } }, "doubleVariables": { "nested": { "path": "doubleVariables" }, "aggs": { "variable_count": { "value_count": { "field": "doubleVariables.id" } } } }, "dateVariables": { "nested": { "path": "dateVariables" }, "aggs": { "variable_count": { "value_count": { "field": "dateVariables.id" } } } }, "booleanVariables": { "nested": { "path": "booleanVariables" }, "aggs": { "variable_count": { "value_count": { "field": "booleanVariables.id" } } } } }}' | jq 'reduce (.aggregations | .. | objects | .doc_count | select(. != null)) as \$total (0; . + \$total)') || true
                NUMBER_OF_DECISION_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-decision-instance/_search?size=0' | jq '.hits.total') || true
               
                # note: each call here is followed by `|| error=true` to not let the whole script fail if one assert fails
                # a final if block checks if there was an error and will let the script fail
                echo "NUMBER_OF_PROCESS_INSTANCES"
                test "\$NUMBER_OF_PROCESS_INSTANCES" = "${EXPECTED_NUMBER_OF_PROCESS_INSTANCES}" || error=true
                echo "NUMBER_OF_ACTIVITY_INSTANCES"
                test "\$NUMBER_OF_ACTIVITY_INSTANCES" = "${EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES}" || error=true
                echo "NUMBER_OF_USER_TASKS"
                test "\$NUMBER_OF_USER_TASKS" = "${EXPECTED_NUMBER_OF_USER_TASKS}" || error=true
                echo "NUMBER_OF_VARIABLES"
                test "\$NUMBER_OF_VARIABLES" = "${EXPECTED_NUMBER_OF_VARIABLES}" || error=true
                echo "NUMBER_OF_DECISION_INSTANCES"
                test "\$NUMBER_OF_DECISION_INSTANCES" = "${EXPECTED_NUMBER_OF_DECISION_INSTANCES}" || error=true 

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
    }
  }
}
