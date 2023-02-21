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
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 2
        memory: 2Gi
"""
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
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage('Prepare') {
      steps {
        container('gcloud') {
          sh 'apk add --no-cache jq gettext'
          camundaInstallKubectl()
          sh """
            bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${SQL_DUMP}" "${ES_VERSION}" "${CAMBPM_VERSION}" "30s" "false" "${ES_NUM_NODES}"
          """
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
          runMaven('-T\$LIMITS_CPU -pl backend -am -DskipTests -Dskip.fe.build -Dskip.docker clean install')
          runMaven("-Pengine-cleanup-performance -f qa/cleanup-performance-tests/pom.xml test -Ddb.url=jdbc:postgresql://postgres.${NAMESPACE}:5432/engine -Dengine.url=http://cambpm.${NAMESPACE}:8080/engine-rest -Des.host=elasticsearch.${NAMESPACE} -Dcleanup.timeout.minutes=${CLEANUP_TIMEOUT_MINUTES}")
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

