#!/usr/bin/env groovy

def static NODE_POOL() { return "slaves-stable" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

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
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
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
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 1, unit: 'HOURS')
  }

  stages {
    stage('Prepare') {
      steps {
        container('gcloud') {
          git url: 'git@github.com:camunda/camunda-optimize',
                  branch: "${params.BRANCH}",
                  credentialsId: 'camunda-jenkins-github-ssh',
                  poll: false
          sh ("""
                # install jq
                apk add --no-cache jq
                # kubectl
                gcloud components install kubectl --quiet

                kubectl apply -f .ci/podSpecs/clusterTests/ns.yml

                #Spawning elasticsearch
                kubectl apply -f .ci/podSpecs/clusterTests/elasticsearch-cfg.yml
                kubectl apply -f .ci/podSpecs/clusterTests/elasticsearch.yml
                # The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
                # Can be removed when we migrate to kubernetes version > 1.12.0
                #kubectl rollout status -f .ci/podSpecs/clusterTests/elasticsearch.yml --watch=true
                while ! nc -z -w 3 elasticsearch.cluster-optimize 9200; do
                  sleep 15
                done

                #Spawning cambpm
                kubectl apply -f .ci/podSpecs/clusterTests/cambpm-cfg.yml
                kubectl apply -f .ci/podSpecs/clusterTests/cambpm.yml
                kubectl rollout status -f .ci/podSpecs/clusterTests/cambpm.yml --watch=true

                #Spawning optimize-no-import-cluster (No import cluster with >1 replicas)
                kubectl apply -f .ci/podSpecs/clusterTests/optimize-no-import-cluster-cfg.yml
                kubectl apply -f .ci/podSpecs/clusterTests/optimize-no-import-cluster.yml
                # The following command does not work due to https://github.com/kubernetes/kubernetes/issues/52653
                # Can be removed when we migrate to kubernetes version > 1.12.0
                #kubectl rollout status -f .ci/podSpecs/clusterTests/optimize-no-import-cluster.yml --watch=true
                while ! nc -z -w 3 optimize-no-import.cluster-optimize 8090; do
                  sleep 15
                done

                #Monitoring Import of optimize-no-import-cluster (Should be false immediately and no data imported)
                curl 'http://optimize-no-import.cluster-optimize:8090/api/status'
                curl 'http://elasticsearch.cluster-optimize:9200/_cat/indices?v'
                until [ \$(curl 'http://optimize-no-import.cluster-optimize:8090/api/status' | jq '.isImporting."camunda-bpm"') = "false" ]; do
                    curl 'http://optimize-no-import.cluster-optimize:8090/api/status'
                    curl 'http://elasticsearch.cluster-optimize:9200/_cat/indices?v'
                    sleep 10
                done

                #Spawning optimize-import (Will import data from engine, only one instance)
                kubectl apply -f .ci/podSpecs/clusterTests/optimize-import-cfg.yml
                kubectl apply -f .ci/podSpecs/clusterTests/optimize-import.yml
                kubectl rollout status -f .ci/podSpecs/clusterTests/optimize-import.yml --watch=true

                #Monitoring Import of optimize-import (Should be true till data got imported)
                until [ \$(curl 'http://optimize-import.cluster-optimize:8090/api/status' | jq '.isImporting."camunda-bpm"') = "false" ]; do
                    curl 'http://optimize-import.cluster-optimize:8090/api/status'
                    curl 'http://elasticsearch.cluster-optimize:9200/_cat/indices?v'
                    sleep 10
                done
            """)
        }
        container('maven') {
          sh ("""apt-get update && apt-get install -y jq netcat""")
        }
      }
    }
    stage('Test') {
      steps {
        container('maven') {
          sh ("""
                mvn -B -Pclustering-tests verify -pl qa/clustering-tests -s settings.xml \
                  -Doptimize.importing.host=optimize-import.cluster-optimize -Doptimize.importing.port=8090 \
                  -Doptimize.notImporting.host=optimize-no-import.cluster-optimize -Doptimize.notImporting.port=8090 \
                  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn 
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
        sh ("kubectl delete -f .ci/podSpecs/clusterTests/ns.yml")
      }
    }
  }
}
