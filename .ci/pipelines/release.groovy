#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
def static NODE_POOL() { return "slaves" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.5.3-jdk-8-slim" }
def static NODEJS_DOCKER_IMAGE() { return "node:8.11.2-alpine" }
def static DIND_DOCKER_IMAGE() { return "docker:18.03.1-ce-dind" }
def static PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }

static String mavenNodeJSDindAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  containers:
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
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 1
        memory: 2Gi
      requests:
        cpu: 1
        memory: 2Gi
  - name: node
    image: ${NODEJS_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
    resources:
      limits:
        cpu: 2
        memory: 1Gi
      requests:
        cpu: 2
        memory: 1Gi
  - name: docker
    image: ${DIND_DOCKER_IMAGE()}
    args: ["--storage-driver=overlay2"]
    securityContext:
      privileged: true
    tty: true
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

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenNodeJSDindAgent(env)
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '1.0.0', description: 'Version to release. Applied to pom.xml and Git tag.')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '1.1.0-SNAPSHOT', description: 'Next development version.')
  }

  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr:'50', artifactNumToKeepStr: '3'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize',
            branch: 'master',
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false

        container('maven') {
          sh ('''
            apt-get update && apt-get -y install git openssh-client
            
            # setup ssh for github
            mkdir -p ~/.ssh
            ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
            
            # git config
            git config --global user.email "ci@camunda.com"
            git config --global user.name "camunda-jenkins"
          ''')
        }
      }
    }
    stage('Maven Release') {
      steps {
        container('node') {
          sh ('''
            cd ./client
            ./build_client.sh $(pwd)
          ''')
        }

        container('maven') {
          sshagent(['camunda-jenkins-github-ssh']) {
            sh ("""
              mvn -Dskip.fe.build=true -DskipTests -Prelease,production,engine-latest release:prepare release:perform \
              -Dtag=${params.RELEASE_VERSION} -DreleaseVersion=${params.RELEASE_VERSION} -DdevelopmentVersion=${params.DEVELOPMENT_VERSION} \
              --settings=settings.xml '-Darguments=--settings=settings.xml -Dskip.fe.build=true -DskipTests -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn' \
              -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
            """)
          }
        }

        container('jnlp') {
          sshagent(['jenkins-camunda-web']) {
            sh ("""#!/bin/bash -xe
              ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no jenkins_camunda_web@vm29.camunda.com "mkdir -p /var/www/camunda/camunda.org/enterprise-release/optimize/${params.RELEASE_VERSION}/"
              for file in distro/target/*.{tar.gz,zip}; do
                scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \${file} jenkins_camunda_web@vm29.camunda.com:/var/www/camunda/camunda.org/enterprise-release/optimize/${params.RELEASE_VERSION}/
              done
            """)
          }
        }
      }
    }
    stage('Docker Image') {
      environment {
        VERSION = "${params.RELEASE_VERSION}"
        REGISTRY = credentials('docker-registry-ci3')
      }
      steps {
        container('docker') {
          sh """
            echo '${REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin
            
            docker build -t ${PROJECT_DOCKER_IMAGE()}:${VERSION} \
              --build-arg=VERSION=${VERSION} \
              --build-arg=SNAPSHOT=false \
              --build-arg=USERNAME=${NEXUS_USR} \
              --build-arg=PASSWORD=${NEXUS_PSW} \
              .

            docker push ${PROJECT_DOCKER_IMAGE()}:${VERSION}
          """
        }
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
  }
}
