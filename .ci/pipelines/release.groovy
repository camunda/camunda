#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
// We can't use maven-alpine because 'frontend-maven-plugin' is incompatible
// Issue: https://github.com/eirslett/frontend-maven-plugin/issues/633
def static MAVEN_DOCKER_IMAGE() { return "maven:3-openjdk-17" }

String getGitCommitMsg() {
  return sh(script: 'git log --format=%B -n 1 HEAD', returnStdout: true).trim()
}

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

void runRelease(params) {
  def pushChanges = 'true'
  def skipDeploy = 'false'

  if (!params.PUSH_CHANGES) {
    pushChanges = 'false'
    skipDeploy = 'true'
  }
  configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("""

    mvn release:prepare release:perform -P -docker -DpushChanges=${pushChanges} -DlocalCheckout=true -DskipTests=true -B -T\$LIMITS_CPU --fail-at-end \
      -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn --settings=\$MAVEN_SETTINGS_XML \
      -Dtag=${params.RELEASE_VERSION} -DreleaseVersion=${params.RELEASE_VERSION} -DdevelopmentVersion=${params.DEVELOPMENT_VERSION} \
      "-Darguments=--settings=\$MAVEN_SETTINGS_XML -P -docker -DskipTests=true -DskipNexusStagingDeployMojo=${skipDeploy} -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    """)
  }
}

def githubRelease = '''\
#!/bin/bash

CURRENT_BRANCH=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

if [[ $CURRENT_BRANCH =~ ^1.3..*-SNAPSHOT$ ]]
then
    ARTIFACT="camunda-cloud-operate"
else
    ARTIFACT="camunda-operate"
fi

ZEEBE_VERSION=$(mvn help:evaluate -Dexpression=version.zeebe -q -DforceStdout)

cd target/checkout/distro/target

# create checksums
sha1sum ${ARTIFACT}-${RELEASE_VERSION}.tar.gz > ${ARTIFACT}-${RELEASE_VERSION}.tar.gz.sha1sum
sha1sum ${ARTIFACT}-${RELEASE_VERSION}.zip > ${ARTIFACT}-${RELEASE_VERSION}.zip.sha1sum

# upload to github release
curl -sL  https://github.com/github-release/github-release/releases/download/v0.10.0/linux-amd64-github-release.bz2 | bzip2 -fd - > github-release
chmod +x github-release
for f in ${ARTIFACT}-${RELEASE_VERSION}.{tar.gz,zip}{,.sha1sum}; do
	./github-release upload --user camunda --repo zeebe --tag ${ZEEBE_VERSION} --name "${f}" --file "${f}"
done
'''

static String mavenAgent(env) {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
    camunda.cloud/source: jenkins
    camunda.cloud/managed-by: "${env.JENKINS_DOMAIN}"
  annotations:
    camunda.cloud/created-by: "${env.BUILD_URL}"
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:MaxRAMFraction=\$(LIMITS_CPU)
      - name: CI
        value: "true"
    resources:
      limits:
        cpu: 2
        memory: 4Gi
      requests:
        cpu: 2
        memory: 4Gi
  - name: docker
    image: docker:20.10.21-dind
    args: ["--storage-driver=overlay2"]
    securityContext:
      privileged: true
    env:
      # This disabled automatic TLS setup as this is not exposed to the network anyway
      - name: DOCKER_TLS_CERTDIR
        value: ""
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 4Gi
      requests:
        cpu: 1
        memory: 4Gi
"""
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenAgent(env)
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '1.0.0', description: 'Version to release. Applied to pom.xml and Git tag.')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '1.1.0-SNAPSHOT', description: 'Next development version.')
    string(name: 'BRANCH', defaultValue: 'master', description: 'Branch to build the release from.')
    booleanParam(name: 'PUSH_CHANGES', defaultValue: true, description: 'Should the changes be pushed to remote locations (Nexus).')
    booleanParam(name: 'GITHUB_UPLOAD_RELEASE', defaultValue: true, description: 'Should upload the release to github.')
    booleanParam(name: 'IS_LATEST', defaultValue: true, description: 'Should tag the docker image with "latest" tag.')
  }

  environment {
    NODE_ENV = "ci"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr:'50', artifactNumToKeepStr: '3'))
    timestamps()
    timeout(time: 45, unit: 'MINUTES')
    withCredentials([
      usernamePassword(passwordVariable: 'NEXUS_PSW', usernameVariable: 'NEXUS_USR', credentialsId: 'camunda-nexus'),
      usernamePassword(passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USERNAME', credentialsId: 'github-operate-app')
    ])
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'https://github.com/camunda/operate.git',
            branch: "${params.BRANCH}",
            credentialsId: 'github-operate-app',
            poll: false

        container('maven') {
          sh ('''
            # git is required for maven release
            apt-get update && apt-get install -y git

            git config --global user.email "ci@operate.camunda.cloud"
            git config --global user.name "github-operate-app"
          ''')
        }
      }
    }
    stage('Maven Release') {
      steps {
        container('maven') {
          runRelease(params)
        }
      }
    }
    stage('Upload to GitHub Release') {
      when { expression { return params.GITHUB_UPLOAD_RELEASE } }
      steps {
        container('maven') {
          sh githubRelease
        }
      }
    }
    stage('Docker Hub Release'){
      when { expression { return params.PUSH_CHANGES } }
        environment {
          IMAGE_NAME = 'camunda/operate'
          IMAGE_TAG = "${params.RELEASE_VERSION}"
          DOCKER_HUB = credentials('camunda-dockerhub')
          VERSION = "${params.RELEASE_VERSION}"
          REVISION = getGitCommitHash()
          DATE = java.time.Instant.now().toString()
          IS_LATEST = "${params.IS_LATEST}"
        }
        steps {
          container('docker') {
            sh """
              docker login --username ${DOCKER_HUB_USR} --password ${DOCKER_HUB_PSW}
              docker buildx create --use

              export VERSION=${VERSION}
              export DATE=${DATE}
              export REVISION=${REVISION}
              export BASE_IMAGE=eclipse-temurin:17-jre-focal
              apk update
              apk add jq
              apk --no-cache add bash

              # Since docker buildx doesn't allow to use --load for a multi-platform build, we do it one at a time to be
              # able to perform the checks before pushing
              # First amd64
              docker buildx build \\
                -t ${IMAGE_NAME}:${IMAGE_TAG} \\
                --build-arg VERSION=${VERSION} \\
                --build-arg DATE=${DATE} \\
                --build-arg REVISION=${REVISION} \\
                --platform linux/amd64 \\
                --load \\
                .
              export ARCHITECTURE=amd64
              bash ./.ci/docker/test/verify.sh ${IMAGE_NAME}:${IMAGE_TAG}

              # Now arm64
              docker buildx build \\
                -t ${IMAGE_NAME}:${IMAGE_TAG} \\
                --build-arg VERSION=${VERSION} \\
                --build-arg DATE=${DATE} \\
                --build-arg REVISION=${REVISION} \\
                --platform linux/arm64 \\
                --load \\
                .
              export ARCHITECTURE=arm64
              bash ./.ci/docker/test/verify.sh ${IMAGE_NAME}:${IMAGE_TAG}

              docker buildx build . \
              --platform linux/arm64,linux/amd64 \
              --build-arg VERSION=${VERSION} \
              --build-arg REVISION=${REVISION} \
              --build-arg DATE=${DATE} \
              -t ${IMAGE_NAME}:${IMAGE_TAG}  \
              --push

              if ${IS_LATEST}; then
                docker buildx build . \
                --platform linux/arm64,linux/amd64 \
                --build-arg VERSION=${VERSION} \
                --build-arg REVISION=${REVISION} \
                --build-arg DATE=${DATE} \
                -t ${IMAGE_NAME}:latest \
                --push
              fi
            """
          }
        }
      }
  }

  post {
    failure {
      script {
        def notification = load "${pwd()}/.ci/pipelines/build_notification.groovy"
        notification.buildNotification(currentBuild.result)

        // Do not send notification on failed test releases
        if (params.PUSH_CHANGES) {
          slackSend(
            channel: "#operate-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
            message: "Release job build ${currentBuild.absoluteUrl} failed!")
        }
      }
    }
  }
}
