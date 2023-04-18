#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static ZEEBE_TASKLIST_DOCKER_IMAGE() { return "registry.camunda.cloud/team-operate/tasklist" }

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

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'zeebe-tasklist-ci'
      label "zeebe-tasklist-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
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
            mvn com.coveo:fmt-maven-plugin:check clean deploy -s $MAVEN_SETTINGS_XML -P -docker -DskipTests=true -B -T$LIMITS_CPU --fail-at-end \
                -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
          '''
          }
        }
      }
    }
    stage('Deploy - Docker Image') {
      environment {
        IMAGE_TAG = getImageTag()
        HARBOR_REGISTRY = credentials('camunda-nexus')
        CI_IMAGE_TAG = getCiImageTag()
      }
      steps {
        lock('zeebe-tasklist-dockerimage-upload') {
          container('maven') {
            configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
              sh """
                mvn -B -s $MAVEN_SETTINGS_XML -pl webapp jib:build -Dimage=${ZEEBE_TASKLIST_DOCKER_IMAGE()}:${IMAGE_TAG}
                mvn -B -s $MAVEN_SETTINGS_XML -pl webapp jib:build -Dimage=${ZEEBE_TASKLIST_DOCKER_IMAGE()}:${CI_IMAGE_TAG}

                if [ "${env.BRANCH_NAME}" = 'master' ]; then
                  mvn -B -s $MAVEN_SETTINGS_XML -pl webapp jib:build -Dimage=${ZEEBE_TASKLIST_DOCKER_IMAGE()}:latest
                fi
              """
            }
          }
        }
      }
    }
    stage('Tests') {
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
                  mvn verify -s $MAVEN_SETTINGS_XML -P -docker,skipFrontendBuild -B -T$LIMITS_CPU --fail-at-end
                  '''
              }
            }
          }
          post {
            always {
              junit testResults: 'qa/integration-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
              junit testResults: 'importer/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
              publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: false, reportDir: 'test-coverage/target/site/jacoco-aggregate', reportFiles: 'index.html', reportName: 'JaCoCo report', reportTitles: 'Tasklist code coverage report', useWrapperFileDirectly: true])
            }
          }
        }
      }
    }
    stage('Deploy') {
      parallel {
        stage('Deploy - Docker Image SNAPSHOT') {
          when {
              branch 'master'
          }
          environment {
            IMAGE_NAME = 'camunda/tasklist'
            IMAGE_TAG = 'SNAPSHOT'
            DOCKER_HUB = credentials('camunda-dockerhub')
          }
          steps {
            lock('zeebe-tasklist-dockerimage-snapshot-upload') {
              container('maven') {
                configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                  sh """
                    mvn -B -s $MAVEN_SETTINGS_XML -pl webapp jib:build -Dimage=${IMAGE_NAME}:${IMAGE_TAG} -Djib.to.auth.username=${DOCKER_HUB_USR} -Djib.to.auth.password=${DOCKER_HUB_PSW}
                  """
                }
              }
            }
          }
        }
        stage('Deploy - Nexus Snapshot') {
          when {
              branch 'master'
          }
          steps {
            lock('zeebe-tasklist-snapshot-upload') {
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
      }
    }
  }

  post {
    always {
      // Retrigger the build if the node disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
    changed {
      script {
        // Do not send notification if the node disconnected
        if (env.BRANCH_NAME == 'master' && !agentDisconnected()) {
          slackSend(
              channel: "#zeebe-tasklist-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
              message: "Tasklist ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
        }
      }
    }
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
  }
}
