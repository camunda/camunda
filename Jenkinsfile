#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static OPERATE_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-operate" }

String getBranchSlug() {
  return env.BRANCH_NAME.toLowerCase().replaceAll(/[^a-z0-9-]/, '-').minus('-deploy')
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

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${env.BUILD_URL}consoleFull"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
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

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(daysToKeepStr:'14', numToKeepStr:'50'))
    timestamps()
    timeout(time: 45, unit: 'MINUTES')
  }

  stages {
    stage('Frontend - Build') {
      steps {
        container('node') {
          sh '''
            cd ./client
            yarn install --frozen-lockfile
            yarn run eslint src/
            yarn build
          '''
        }
      }
    }
    stage('Backend - Build') {
      steps {
        container('maven') {
          // MaxRAMFraction = LIMITS_CPU because there are only maven build threads
          sh '''
            JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))" \
            mvn clean deploy -s settings.xml -P -docker,skipFrontendBuild -DskipTests=true -B -T$LIMITS_CPU --fail-at-end \
                -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
          '''
        }
      }
    }
    stage('Unit tests') {
      parallel {
        stage('Backend - Tests') {
          steps {
            container('maven') {
              // MaxRAMFraction = LIMITS_CPU+1 because there are LIMITS_CPU surefire threads + one maven thread
              sh '''
                JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+1))" \
                mvn verify -s settings.xml -P -docker -B -T$LIMITS_CPU --fail-at-end
              '''
            }
          }
          post {
            always {
              junit testResults: 'qa/integration-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
              junit testResults: 'importer/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
        stage('Frontend - Tests') {
          steps {
            container('node') {
              sh '''
                cd ./client
                yarn test:ci
              '''
            }
          }
          post {
            always {
              junit testResults: 'client/jest-test-results.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
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
                sh '''
                  mvn org.sonatype.plugins:nexus-staging-maven-plugin:deploy-staged -DskipTests=true  -P -docker,skipFrontendBuild -B --fail-at-end \
                    -s settings.xml \
                    -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
                '''
              }
            }
          }
        }
        stage('Deploy - Docker Image') {
          when {
            expression { BRANCH_NAME ==~ /(master|.*-deploy)/ }
          }
          environment {
            IMAGE_TAG = getImageTag()
            GCR_REGISTRY = credentials('docker-registry-ci3')
          }
          steps {
            lock('operate-dockerimage-upload') {
              container('docker') {
                sh """
                  echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin

                  docker build -t ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} .

                  docker push ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG}

                  if [ "${env.BRANCH_NAME}" = 'master' ]; then
                    docker tag ${OPERATE_DOCKER_IMAGE()}:${IMAGE_TAG} ${OPERATE_DOCKER_IMAGE()}:latest
                    docker push ${OPERATE_DOCKER_IMAGE()}:latest
                  fi
                """
              }
            }
          }
        }
      }
    }
    stage ('Deploy to K8s') {
      when {
        expression { BRANCH_NAME ==~ /(master|.*-deploy)/ }
      }
      steps {
        build job: '/deploy-branch-to-k8s',
          parameters: [
              string(name: 'BRANCH', value: getBranchSlug()),
              string(name: 'OPERATE_BRANCH', value: env.BRANCH_NAME),
          ]
      }
    }
  }

  post {
    changed {
      script {
        if (!nodeDisconnected()) {
          buildNotification(currentBuild.result)
        }
      }
    }
    always {
      // Retrigger the build if the node disconnected
      script {
        if (nodeDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

boolean nodeDisconnected() {
  return currentBuild.rawBuild.getLog(500).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException).*/
}
