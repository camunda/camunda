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
      serviceAccount 'ci-optimize-camunda-cloud'
      yaml gCloudAndMavenAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "optimize-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
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
          setBuildEnvVars()
        }
      }
    }
    stage('Prepare') {
      steps {
        container('gcloud') {
          sh 'apk add --no-cache jq gettext postgresql-client'
          camundaInstallKubectl()
          sh("""
            bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${SQL_DUMP}" "${ES_VERSION}" "${CAMBPM_VERSION}" "${ES_REFRESH_INTERVAL}" "false" "${ES_NUM_NODES}"
          """)
        }
      }
    }
    stage('ImportTest') {
      steps {
        container('gcloud') {
          sh("""
                bash .ci/podSpecs/performanceTests/wait-for-import-to-finish.sh "${NAMESPACE}"

                curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/_refresh'

                # assert expected counts
                # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                NUMBER_OF_PROCESS_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_count' | jq '.count') || true
                NUMBER_OF_ACTIVITY_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"flowNodes": {"nested": {"path": "flowNodeInstances"}}}}' | jq '.aggregations.flowNodes.doc_count') || true
                NUMBER_OF_USER_TASKS=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size":0,"aggs":{"flowNodes":{"nested":{"path":"flowNodeInstances"},"aggs":{"user_task_flow_nodes":{"filter":{"bool":{"must":[{"term":{"flowNodeInstances.flowNodeType":{"value":"userTask","boost":1.0}}}],"adjust_pure_negative":true,"boost":1.0}}}}}}}' | jq '.aggregations.flowNodes.user_task_flow_nodes.doc_count') || true
                NUMBER_OF_INCOMPLETE_USER_TASKS=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size":0,"aggs":{"flowNodes":{"nested":{"path":"flowNodeInstances"},"aggs":{"user_task_incomplete_flow_nodes":{"filter":{"bool":{"must":[{"term":{"flowNodeInstances.flowNodeType":{"value":"userTask","boost":1.0}}}],"must_not":[{"exists":{"field":"flowNodeInstances.flowNodeInstanceId"}}],"adjust_pure_negative":true,"boost":1.0}}}}}}}' | jq '.aggregations.flowNodes.user_task_incomplete_flow_nodes.doc_count') || true
                NUMBER_OF_VARIABLES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0, "aggs": {"variables": {"nested": { "path": "variables" },  "aggs": { "variable_count": { "value_count": { "field": "variables.id" } } } } } }' | jq '.aggregations.variables.doc_count') || true
                NUMBER_OF_DECISION_INSTANCES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-decision-instance/_count' | jq '.count') || true

                # note: each call here is followed by `|| true` to not let the whole script fail if one assert fails
                # a final if block checks if there was an error and will let the script fail
                EXPECTED_NUMBER_OF_PROCESS_INSTANCES=\$(psql -qAt -h postgres.${NAMESPACE} -U camunda -d engine -c "select count(*) from act_hi_procinst;") || true
                EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES=\$(psql -qAt -h postgres.${NAMESPACE} -U camunda -d engine -c "select count(*) from act_hi_actinst;") || true
                EXPECTED_NUMBER_OF_USER_TASKS=\$(psql -qAt -h postgres.${NAMESPACE} -U camunda -d engine -c "select count(*) as total from act_hi_taskinst;") || true
                EXPECTED_NUMBER_OF_VARIABLES=\$(psql -qAt -h postgres.${NAMESPACE} -U camunda -d engine -c "select count(*) from act_hi_varinst where CASE_INST_ID_  is  null;") || true
                EXPECTED_NUMBER_OF_DECISION_INSTANCES=\$(psql -qAt -h postgres.${NAMESPACE} -U camunda -d engine -c "select count(*) from act_hi_decinst;") || true

                echo "NUMBER_OF_DECISION_INSTANCES"
                test "\$NUMBER_OF_DECISION_INSTANCES" = "\${EXPECTED_NUMBER_OF_DECISION_INSTANCES}" || error=true
                echo "NUMBER_OF_PROCESS_INSTANCES"
                test "\$NUMBER_OF_PROCESS_INSTANCES" = "\${EXPECTED_NUMBER_OF_PROCESS_INSTANCES}" || error=true
                echo "NUMBER_OF_ACTIVITY_INSTANCES"
                test "\$NUMBER_OF_ACTIVITY_INSTANCES" = "\${EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES}" || error=true
                echo "NUMBER_OF_VARIABLES"
                test "\$NUMBER_OF_VARIABLES" -ge "\${EXPECTED_NUMBER_OF_VARIABLES}" || error=true
                echo "NUMBER_OF_USER_TASKS"
                test "\$NUMBER_OF_USER_TASKS" = "\${EXPECTED_NUMBER_OF_USER_TASKS}" || error=true
                echo "NUMBER_OF_INCOMPLETE_USER_TASKS"
                test "\$NUMBER_OF_INCOMPLETE_USER_TASKS" = "0" || error=true

                # Fail the build if there was an error
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
        sh("bash .ci/podSpecs/performanceTests/kill.sh \"${NAMESPACE}\"")
      }
      retriggerBuildIfDisconnected()
    }
  }
}
