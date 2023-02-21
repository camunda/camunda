#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }

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
    NAMESPACE = "optimize-cluster-test-${env.BUILD_ID}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 1, unit: 'HOURS')
  }

  stages {
    stage('Retrieve CamBPM and Elasticsearch version') {
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage('Prepare') {
      steps {
        container('gcloud') {
          sh 'apk add --no-cache jq gettext'
          camundaInstallKubectl()
          sh("""
            bash .ci/podSpecs/clusterTests/deploy.sh "${NAMESPACE}" "${ES_VERSION}" "${CAMBPM_VERSION}"
          """)
        }
        container('maven') {
          sh("""apt-get update && apt-get install -y jq netcat""")
        }
      }
    }
    stage('Test') {
      steps {
        container('maven') {
          configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
            sh("""
                mvn -B -Pclustering-tests verify -pl qa/clustering-tests -s \$MAVEN_SETTINGS_XML \
                  -Doptimize.importing.host=optimize-import.${NAMESPACE} -Doptimize.importing.port=8090 \
                  -Doptimize.notImporting.host=optimize-no-import.${NAMESPACE} -Doptimize.notImporting.port=8090 \
                  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
            """)
          }
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
        sh("bash .ci/podSpecs/clusterTests/kill.sh \"${NAMESPACE}\"")
      }
      retriggerBuildIfDisconnected()
    }
  }
}

