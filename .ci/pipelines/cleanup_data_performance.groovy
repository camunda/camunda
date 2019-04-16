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
          -XX:MaxRAMFraction=2
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 2Gi
      requests:
        cpu: 3
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
                apk update && apk add jq && rm -rf /var/cache/apk/*
                # kubectl
                gcloud components install kubectl --quiet

                kubectl apply -f .ci/podSpecs/performanceTests/ns.yml
                kubectl create secret docker-registry registry-camunda-cloud-secret --namespace performance-optimize --docker-server=https://registry.camunda.cloud --docker-username="${REGISTRY_USR}" --docker-password="${REGISTRY_PSW}" --docker-email=ci@camunda.com

                kubectl apply -f .ci/podSpecs/performanceTests/rbac.yml
                
                # Spawning postgres
                kubectl apply -f .ci/podSpecs/performanceTests/postgresql-cfg.yml
                kubectl apply -f .ci/podSpecs/performanceTests/postgresql.yml
                # The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
                # Can be removed when we migrate to kubernetes version > 1.12.0
                #kubectl rollout status -f .ci/podSpecs/performanceTests/postgresql.yml --watch=true
                while ! nc -z -w 3 postgres.performance-optimize 5432; do
                  sleep 15
                done

                # Import data in postgresql
                POD_NAME=\$(kubectl get po -n performance-optimize | grep postgres | cut -f1 -d' ')
                kubectl exec -n performance-optimize \$POD_NAME -c gcloud -it -- gsutil -q -m cp gs://camunda-ops/optimize/${SQL_DUMP} /db_dump/dump.sqlc
                kubectl exec -n performance-optimize \$POD_NAME -c postgresql -it -- pg_restore --clean --if-exists -v -j 16 -h localhost -U camunda -d engine /db_dump/dump.sqlc

                #Spawning elasticsearch
                kubectl apply -f .ci/podSpecs/performanceTests/elasticsearch-cfg.yml
                kubectl apply -f .ci/podSpecs/performanceTests/elasticsearch.yml
                # The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
                # Can be removed when we migrate to kubernetes version > 1.12.0
                #kubectl rollout status -f .ci/podSpecs/performanceTests/elasticsearch.yml --watch=true
                while ! nc -z -w 3 elasticsearch.performance-optimize 9200; do
                  sleep 15
                done

                #Spawning cambpm
                kubectl apply -f .ci/podSpecs/performanceTests/cambpm-cfg.yml
                kubectl apply -f .ci/podSpecs/performanceTests/cambpm.yml

                kubectl rollout status -f .ci/podSpecs/performanceTests/cambpm.yml --watch=true

                #Spawning optimize
                kubectl apply -f .ci/podSpecs/performanceTests/optimize-cfg.yml
                kubectl -n performance-optimize create configmap performance-optimize-camunda-cloud --from-file=.ci/podSpecs/performanceTests/optimize-config/
                kubectl apply -f .ci/podSpecs/performanceTests/optimize.yml

                kubectl rollout status -f .ci/podSpecs/performanceTests/optimize.yml --watch=true
            """)
        }
      }
    }
    stage('Import') {
      steps {
        container('gcloud') {
          sh ("""
                #Monitoring Import of optimize-import (Should be true till data got imported)
                IMPORTING="true"
                until [ \$IMPORTING = "false" ]; do
                    # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                    curl -s -X POST 'http://elasticsearch.performance-optimize:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"events": {"nested": {"path": "events"},"aggs": {"event_count": {"value_count": {"field": "events.id"}}}}}}' || true
                    curl -s -X POST 'http://elasticsearch.performance-optimize:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"userTasks": {"nested": {"path": "userTasks"},"aggs": {"user_task_count": {"value_count": {"field": "userTasks.id"}}}}}}' || true
                    curl -s -X POST 'http://elasticsearch.performance-optimize:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0, "aggs": {"stringVariables": {"nested": {"path": "stringVariables" }, "aggs": { "variable_count": { "value_count": { "field": "stringVariables.id" } } } }, "integerVariables": { "nested": { "path": "integerVariables" }, "aggs": { "variable_count": { "value_count": { "field": "integerVariables.id" } } } }, "longVariables": { "nested": { "path": "longVariables" }, "aggs": { "variable_count": { "value_count": { "field": "longVariables.id" } } } }, "shortVariables": { "nested": { "path": "shortVariables" }, "aggs": { "variable_count": { "value_count": { "field": "shortVariables.id" } } } }, "doubleVariables": { "nested": { "path": "doubleVariables" }, "aggs": { "variable_count": { "value_count": { "field": "doubleVariables.id" } } } }, "dateVariables": { "nested": { "path": "dateVariables" }, "aggs": { "variable_count": { "value_count": { "field": "dateVariables.id" } } } }, "booleanVariables": { "nested": { "path": "booleanVariables" }, "aggs": { "variable_count": { "value_count": { "field": "booleanVariables.id" } } } } }}' || true
                    curl -s -X GET 'http://elasticsearch.performance-optimize:9200/optimize-decision-instance/_search?size=0' || true
                    curl -s -X GET 'http://elasticsearch.performance-optimize:9200/optimize-timestamp-based-import-index/_search?size=20' || true
                    curl -s 'http://elasticsearch.performance-optimize:9200/_cat/indices?v' || true
                    IMPORTING=\$(curl 'http://optimize.performance-optimize:8090/api/status' | jq '.isImporting."camunda-bpm"') || true
                    sleep 60
                done
                
                sleep 60
                
                sed 's/replicas: 1/replicas: 0/g' .ci/podSpecs/performanceTests/optimize.yml > .ci/podSpecs/performanceTests/optimize_down.yml
                kubectl apply -f .ci/podSpecs/performanceTests/optimize_down.yml
            """)
        }
      }
    }
    stage('CleanupPerformanceTest') {
      steps {
        container('maven') {
          runMaven('-T\$LIMITS_CPU -DskipTests -Dskip.fe.build -Dskip.docker clean install')
          runMaven("-Pcleanup-performance-test -f qa/cleanup-performance-tests/pom.xml test -Ddb.url=jdbc:postgresql://postgres.performance-optimize:5432/engine -Dengine.url=http://cambpm.performance-optimize:8080/engine-rest -Des.host=elasticsearch.performance-optimize -Dcleanup.timeout.minutes=${CLEANUP_TIMEOUT_MINUTES}")
        }
      }
      post {
        always {
          container('maven') {
            sh 'curl -s elasticsearch.performance-optimize:9200/_cat/indices?v || true'
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
        sh ("kubectl delete -f .ci/podSpecs/performanceTests/ns.yml")
      }
    }
  }
}
