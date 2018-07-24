#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }

String calculatePreviousVersion(releaseVersion) {
  def version = releaseVersion.tokenize('.')
  def majorVersion = version[0]
  def minorVersion = version[1]
  def patchVersion = version[2]

  if (!patchVersion.contains('-') && patchVersion == '0') {
    // only major / minor GA (.0) release versions will trigger an auto-update of previousVersion property.
    println 'Auto-updating previousVersion property as release version is a valid major/minor version.'
    return releaseVersion
  } else {
    println 'Not auto-updating previousVersion property as release version is not a valid major/minor version.'
    return ""
  }
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

static String mavenDindAgent() {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  containers:
  - name: maven
    image: maven:3.5.3-jdk-8-slim
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      # every JVM process will get a 1/2 of HEAP from total memory
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=\$(LIMITS_CPU)
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 2
        memory: 3Gi
      requests:
        cpu: 2
        memory: 3Gi
  - name: docker
    image: docker:18.03.1-ce-dind
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

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenDindAgent()
    }
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
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false

        container('maven') {
          sh ('''
            # install git and ssh
            apt-get update && \
            apt-get -y install git openssh-client

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
    stage('Release') {
      environment {
        PREVIOUS_VERSION = calculatePreviousVersion(params.RELEASE_VERSION)
      }
      steps {
        container('maven') {
          sshagent(['camunda-jenkins-github-ssh']) {
            sh ("""
              mvn -DskipTests -Prelease,production,engine-latest release:prepare release:perform \
              -Dtag=${params.RELEASE_VERSION} -DreleaseVersion=${params.RELEASE_VERSION} -DdevelopmentVersion=${params.DEVELOPMENT_VERSION} \
              --settings=settings.xml '-Darguments=--settings=settings.xml -DskipTests -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn' \
              -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
            """)

            sh ("""
              # auto-update previousVersion property
              if [ ! -z "\${PREVIOUS_VERSION}" ]; then
                sed -i "s/project.previousVersion>.*</project.previousVersion>${params.RELEASE_VERSION}</g" pom.xml
                git add pom.xml
                git commit -m "chore(release): update previousVersion to new release version ${params.RELEASE_VERSION}"
                git push origin ${params.BRANCH}
              fi
            """)
          }
        }
      }
      post {
        always {
          archiveArtifacts(
              artifacts: "target/checkout/distro/target/*.zip,target/checkout/distro/target/*.tar.gz",
              onlyIfSuccessful: false
          )
        }
      }
    }
    stage('Upload camunda.org') {
      steps {
        container('jnlp') {
          sshagent(['jenkins-camunda-web']) {
            sh ("""#!/bin/bash -xe
              ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no jenkins_camunda_web@vm29.camunda.com "mkdir -p /var/www/camunda/camunda.org/enterprise-release/optimize/${params.RELEASE_VERSION}/"
              for file in target/checkout/distro/target/*.{tar.gz,zip}; do
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
        GCR_REGISTRY = credentials('docker-registry-ci3')
      }
      steps {
        container('docker') {
          sh ("""
            echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin
            
            docker build -t ${PROJECT_DOCKER_IMAGE()}:${VERSION} \
              --build-arg=VERSION=${VERSION} \
              --build-arg=SNAPSHOT=false \
              --build-arg=USERNAME=${NEXUS_USR} \
              --build-arg=PASSWORD=${NEXUS_PSW} \
              .

            docker push ${PROJECT_DOCKER_IMAGE()}:${VERSION}
          """)
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
