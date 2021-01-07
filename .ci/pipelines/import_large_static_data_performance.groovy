#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.3-jdk-8-slim" }

def static NODE_POOL() { return "agents-n1-standard-8-netssd-stable" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

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
  serviceAccountName: ci-optimize-camunda-cloud
  containers:
  - name: gcloud
    image: google/cloud-sdk:alpine
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 512Mi
      requests:
        cpu: 500m
        memory: 512Mi
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
    timeout(time: 10, unit: 'HOURS')
  }

  stages {
    stage('Retrieve CamBPM and Elasticsearch version') {
      steps {
        container('maven') {
          optimizeCloneGitRepo(params.BRANCH)
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

                bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${SQL_DUMP}" "${ES_VERSION}" "${CAMBPM_VERSION}" "${ES_REFRESH_INTERVAL}" "false" "${ES_NUM_NODES}"
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
                NUMBER_OF_PROCESS_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_count' | jq '.count') || true
                NUMBER_OF_ACTIVITY_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"events": {"nested": {"path": "events"},"aggs": {"event_count": {"value_count": {"field": "events.id"}}}}}}' | jq '.aggregations.events.doc_count') || true
                NUMBER_OF_USER_TASKS=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"userTasks": {"nested": {"path": "userTasks"},"aggs": {"user_task_Count": {"value_count": {"field": "userTasks.id"}}}}}}' | jq '.aggregations.userTasks.doc_count') || true
                NUMBER_OF_VARIABLES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0, "aggs": {"variables": {"nested": { "path": "variables" },  "aggs": { "variable_count": { "value_count": { "field": "variables.id" } } } } } }' | jq '.aggregations.variables.doc_count') || true
                NUMBER_OF_DECISION_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-decision-instance/_count' | jq '.count') || true

                # note: each call here is followed by `|| error=true` to not let the whole script fail if one assert fails
                # a final if block checks if there was an error and will let the script fail
                export EXPECTED_NUMBER_OF_PROCESS_INSTANCES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_process_instances | cut -f2 -d':'`
                export EXPECTED_NUMBER_OF_USER_TASKS=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_user_tasks | cut -f2 -d':'`
                export EXPECTED_NUMBER_OF_VARIABLES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_variables | cut -f2 -d':'`
                export EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_activity_instances | cut -f2 -d':'`
                export EXPECTED_NUMBER_OF_DECISION_INSTANCES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_decision_instances | cut -f2 -d':'`

                echo "NUMBER_OF_PROCESS_INSTANCES"
                test "\$NUMBER_OF_PROCESS_INSTANCES" = "\${EXPECTED_NUMBER_OF_PROCESS_INSTANCES}" || error=true
                echo "NUMBER_OF_ACTIVITY_INSTANCES"
                test "\$NUMBER_OF_ACTIVITY_INSTANCES" = "\${EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES}" || error=true
                echo "NUMBER_OF_USER_TASKS"
                test "\$NUMBER_OF_USER_TASKS" = "\${EXPECTED_NUMBER_OF_USER_TASKS}" || error=true
                echo "NUMBER_OF_VARIABLES"
                test "\$NUMBER_OF_VARIABLES" = "\${EXPECTED_NUMBER_OF_VARIABLES}" || error=true
                echo "NUMBER_OF_DECISION_INSTANCES"
                test "\$NUMBER_OF_DECISION_INSTANCES" = "\${EXPECTED_NUMBER_OF_DECISION_INSTANCES}" || error=true

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
      sendNotification(currentBuild.result,null,null,[[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])
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
