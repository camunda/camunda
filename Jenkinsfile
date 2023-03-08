#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static OPERATE_DOCKER_IMAGE() { return "registry.camunda.cloud/team-operate/camunda-operate" }

String getBranchSlug() {
  return env.BRANCH_NAME.toLowerCase().replaceAll(/[^a-z0-9-]/, '-')
}

String getGitCommitMsg() {
  return sh(script: 'git log --format=%B -n 1 HEAD', returnStdout: true).trim()
}

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

String getImageTag() {
  return env.BRANCH_NAME == 'master' ? getGitCommitHash() : "branch-${getBranchSlug()}"
}

String getCiImageTag() {
  return "ci-${getGitCommitHash()}"
}

String getVersion() {
  return readMavenPom(file: 'pom.xml').getVersion()
}

String getRevision() {
    return getGitCommitHash()
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yamlFile '.ci/podSpecs/builderAgent.yml'
    }
  }

  triggers {
    cron(env.BRANCH_NAME.contains('stable/') ? 'H 3 * * 1-5' : '') // every working day at around 3am on stable branches
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(daysToKeepStr:'14', numToKeepStr:'50'))
    timestamps()
    timeout(time: 90, unit: 'MINUTES')
  }

  stages {
    stage('Backend - Build') {
      steps {
        container('maven') {
          // MaxRAMFraction = LIMITS_CPU because there are only maven build threads
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh '''
            JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))" \
            mvn clean deploy -s $MAVEN_SETTINGS_XML -P -docker -DskipTests=true -B -T$LIMITS_CPU \
                -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
          '''
          }
        }
      }
    }
    stage('Prepare Docker login') {
      environment {
        HARBOR_REGISTRY = credentials('camunda-nexus')
        DOCKER_HUB = credentials('camunda-dockerhub')
      }
      steps {
        container('docker') {
          sh """
            docker login --username ${DOCKER_HUB_USR} --password ${DOCKER_HUB_PSW}
            docker login registry.camunda.cloud --username ${HARBOR_REGISTRY_USR} --password ${HARBOR_REGISTRY_PSW}
          """
        }
      }
    }
    stage('Deploy - Docker Image') {
      environment {
        IMAGE_TAG = getImageTag()
        CI_IMAGE_TAG = getCiImageTag()
        VERSION = getVersion()
        REVISION = getRevision()
        DATE = java.time.Instant.now().toString()
      }
      steps {
        lock('operate-dockerimage-upload') {
          container('docker') {
            sh """
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
              docker buildx build \
                -t ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} \
                -t ${OPERATE_DOCKER_IMAGE()}:${CI_IMAGE_TAG} \
                --build-arg VERSION=${VERSION} \
                --build-arg DATE=${DATE} \
                --build-arg REVISION=${REVISION} \
                --platform linux/amd64 \
                --load \
                .
              export ARCHITECTURE=amd64
              bash ./.ci/docker/test/verify.sh ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} ${OPERATE_DOCKER_IMAGE()}:${CI_IMAGE_TAG}

              # Now arm64
              docker buildx build \
                -t ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} \
                -t ${OPERATE_DOCKER_IMAGE()}:${CI_IMAGE_TAG} \
                --build-arg VERSION=${VERSION} \
                --build-arg DATE=${DATE} \
                --build-arg REVISION=${REVISION} \
                --platform linux/arm64 \
                --load \
                .
              export ARCHITECTURE=arm64
              bash ./.ci/docker/test/verify.sh ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} ${OPERATE_DOCKER_IMAGE()}:${CI_IMAGE_TAG}

              # If we made it to here, all checks were successful. So let's build it to push. This is not as
              # inefficient as it looks, since docker retrieves the previously generated images from the build cache
              docker buildx build . \
                --platform linux/arm64,linux/amd64 \
                --build-arg VERSION=${VERSION} \
                --build-arg REVISION=${REVISION} \
                --build-arg DATE=${DATE} \
                -t ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} \
                -t ${OPERATE_DOCKER_IMAGE()}:${CI_IMAGE_TAG} \
                --push

              if [ "${env.BRANCH_NAME}" = 'master' ]; then
                docker buildx build . \
                --platform linux/arm64,linux/amd64 \
                --build-arg VERSION=${VERSION} \
                --build-arg REVISION=${REVISION} \
                --build-arg DATE=${DATE} \
                -t ${OPERATE_DOCKER_IMAGE()}:latest \
                --push
              fi
            """
          }
        }
      }
    }
    stage('Unit tests') {
      when {
        not {
          expression { BRANCH_NAME ==~ /(^fe-.*)/ }
        }
      }
      parallel {
        stage('Backend - Tests') {
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                // MaxRAMFraction = LIMITS_CPU+1 because there are LIMITS_CPU surefire threads + one maven thread
                sh '''
                  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+OLD_ZEEBE_TESTS_THREADS+3))" \
                  mvn verify -s $MAVEN_SETTINGS_XML -P -docker,skipFrontendBuild -B -T$LIMITS_CPU
                  '''
              }
            }
          }
          post {
            always {
              junit testResults: 'qa/integration-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
              junit testResults: 'importer/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
        //https://github.com/camunda/operate/issues/2471
//        stage('Backend - Tests (old Zeebe)') {
//          steps {
//            container('maven') {
//              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
//                // MaxRAMFraction = LIMITS_CPU+1 because there are LIMITS_CPU surefire threads + one maven thread
//                sh '''
//                  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+OLD_ZEEBE_TESTS_THREADS+3))" \
//                  mvn verify -f qa/integration-tests -s $MAVEN_SETTINGS_XML -P -docker,-skipTests,old-zeebe -B -T$OLD_ZEEBE_TESTS_THREADS --fail-at-end
//                  '''
//              }
//            }
//          }
//          post {
//            always {
//              junit testResults: 'qa/integration-tests/target-old-zeebe/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
//            }
//          }
//        }
      }
    }
    stage('Deploy') {
      parallel {
        stage('Deploy - Nexus Snapshot') {
          when {
              branch 'master'
          }
          steps {
            lock('operate-snapshot-upload') {
              container('maven') {
                configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                  sh '''
                    mvn org.sonatype.plugins:nexus-staging-maven-plugin:deploy-staged -DskipTests=true  -P -docker,skipFrontendBuild -B --fail-at-end \
                      -s $MAVEN_SETTINGS_XML \
                      -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
                  '''
                }
              }
            }
          }
        }
        stage('Deploy - Docker Image SNAPSHOT') {
          when {
              branch 'master'
          }
          environment {
            IMAGE_NAME = 'camunda/operate'
            IMAGE_TAG = 'SNAPSHOT'
            VERSION = getVersion()
            REVISION = getRevision()
            DATE = java.time.Instant.now().toString()
          }
          steps {
            lock('operate-dockerimage-snapshot-upload') {
              container('docker') {
                sh """
                  docker buildx create --use
                  docker buildx build . \
                  --platform linux/arm64,linux/amd64 \
                  --build-arg VERSION=${VERSION} \
                  --build-arg REVISION=${REVISION} \
                  --build-arg DATE=${DATE} \
                  -t ${IMAGE_NAME}:${IMAGE_TAG} \
                  --push
                """
              }
            }
          }
        }
      }
    }
  }

  post {
    unsuccessful {
      // Store container logs
      writeFile(
              file: "elastic_logs.txt",
              text: containerLog( name: "elasticsearch", returnLog: true )
      )
      archiveArtifacts artifacts: '*.txt'
      // Do not send notification if the node disconnected
      script {
        if (!agentDisconnected()) {
          def notification = load ".ci/pipelines/build_notification.groovy"
          notification.buildNotification(currentBuild.result)
        }
      }
    }
    changed {
      script {
        // Do not send notification if the node disconnected
        if (env.BRANCH_NAME == 'master' && !agentDisconnected()) {
          slackSend(
              channel: "#operate-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
              message: "Operate ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
        }
      }
    }
    always {
      // Retrigger the build if the node disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}
