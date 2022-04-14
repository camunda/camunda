#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static ZEEBE_TASKLIST_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/tasklist" }

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
    stage('Prepare') {
      steps {
        container('maven') {
          sh '.ci/scripts/ensure-naming-for-process.sh'
        }
      }
    }
    stage('Frontend - Build') {
      steps {
        container('node') {
          sh '''
            apk add --no-cache git
            cd ./client
            yarn install --frozen-lockfile
            yarn lint
            yarn build
          '''
        }
      }
    }
    stage('Backend - Build') {
      steps {
        container('maven') {
          // MaxRAMFraction = LIMITS_CPU because there are only maven build threads
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh '''
            JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))" \
            mvn com.coveo:fmt-maven-plugin:check clean deploy -s $MAVEN_SETTINGS_XML -P -docker,skipFrontendBuild -DskipTests=true -B -T$LIMITS_CPU --fail-at-end \
                -DaltStagingDirectory=$(pwd)/staging -DskipRemoteStaging=true -Dmaven.deploy.skip=true
          '''
          }
        }
      }
    }
    stage('Tests') {
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

        stage('End to end - Tests'){
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                sh ('mvn -B -s $MAVEN_SETTINGS_XML -DZEEBE_TASKLIST_CSRF_PREVENTION_ENABLED=false spring-boot:start -f webapp/pom.xml -Dspring-boot.run.fork=true')
                sh ('sleep 30')
                sh '''
                  JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU+OLD_ZEEBE_TESTS_THREADS+3))" \
                  mvn -B -s $MAVEN_SETTINGS_XML -f client/pom.xml -P client.e2etests-chromeheadless test
                  '''
                sh ('mvn -B -s $MAVEN_SETTINGS_XML spring-boot:stop -f webapp/pom.xml -Dspring-boot.stop.fork=true')
              }
            }
          }
        }
      }
    }
    stage('Install GCR login helper') {
      steps {
        container('maven') {
          sh """
            curl -L https://github.com/GoogleCloudPlatform/docker-credential-gcr/releases/download/v2.1.0/docker-credential-gcr_linux_amd64-2.1.0.tar.gz --output docker-credential-gcr.tar.gz
            tar -zxvf docker-credential-gcr.tar.gz
            chmod +x docker-credential-gcr
            mv docker-credential-gcr /usr/local/bin/docker-credential-gcr
          """
        }
      }
    }
    stage('Deploy') {
      parallel {
        stage('Deploy - Docker Image') {
          when {
            not {
                expression { BRANCH_NAME ==~ /(.*-nodeploy)/ }
            }
          }
          environment {
            IMAGE_TAG = getImageTag()
            GOOGLE_APPLICATION_CREDENTIALS = credentials('docker-registry-ci3-file')
          }
          steps {
            lock('zeebe-tasklist-dockerimage-upload') {
              container('maven') {
                configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                  sh """
                    mvn -B -s $MAVEN_SETTINGS_XML -pl webapp jib:build -Dimage=${ZEEBE_TASKLIST_DOCKER_IMAGE()}:${IMAGE_TAG}

                    if [ "${env.BRANCH_NAME}" = 'master' ]; then
                      mvn -B -s $MAVEN_SETTINGS_XML -pl webapp jib:build -Dimage=${ZEEBE_TASKLIST_DOCKER_IMAGE()}:latest
                    fi
                  """
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
    stage ('Deploy to K8s') {
      when {
        not {
            expression { BRANCH_NAME ==~ /(.*-nodeploy)/ }
        }
      }
      steps {
        build job: '/deploy-branch-to-k8s',
          parameters: [
              string(name: 'BRANCH', value: getBranchSlug()),
              string(name: 'ZEEBE_TASKLIST_BRANCH', value: env.BRANCH_NAME),
          ]
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
