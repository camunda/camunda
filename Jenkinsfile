#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def static PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

String getBranchSlug() {
  return env.BRANCH_NAME.toLowerCase().replaceAll(/[^a-z0-9-]/, '-').minus('-deploy')
}

String getImageTag() {
  return env.BRANCH_NAME == 'master' ? getGitCommitHash() : "branch-${getBranchSlug()}"
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

void integrationTestSteps(String engineVersion = 'latest') {
  container('maven') {
    installDockerBinaries()
    sh ("""echo '${CAM_REGISTRY_PSW}' | docker login -u ${CAM_REGISTRY_USR} registry.camunda.cloud --password-stdin""")
    setupPermissionsForHostDirs('backend')
    runMaven("install -Pproduction,it,engine-${engineVersion} -pl backend -am -T\$LIMITS_CPU")
  }
}

void setupPermissionsForHostDirs(String directory) {
  sh("""#!/bin/bash -ex
    mkdir -p ${directory}/target/{es_logs,cambpm_logs}
    # must be 1000 so ES and CamBPM can write to the mounted volumes defined in docker-compose.yml
    chown -R 1000:1000 ${directory}/target/{es_logs,cambpm_logs}
  """)
}

void archiveTestArtifacts(String srcDirectory, String destDirectory = null) {
  container('maven') {
    // fix permissions for jnlp slave as maven user is root so we can archive the artifacts
    sh ("""#!/bin/bash -ex
      chown -R 10000:10000 ${srcDirectory}/target/{es_logs,cambpm_logs}
    """)
  }

  if (destDirectory == null) {
    destDirectory = srcDirectory
  } else {
    sh ("""
      mkdir -p ${destDirectory}
      cp -R ${srcDirectory}/target/es_logs ${srcDirectory}/target/cambpm_logs ${destDirectory}
    """)
  }

  archiveArtifacts(
    artifacts: "${destDirectory}/**/*",
    allowEmptyResults: true,
    onlyIfSuccessful: false
  )
}

void installDockerBinaries() {
  sh("""
    curl -sSL https://github.com/docker/compose/releases/download/1.21.2/docker-compose-Linux-x86_64 -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    docker-compose version

    curl -sSL https://download.docker.com/linux/static/stable/x86_64/docker-18.03.1-ce.tgz | \
      tar xvzf -  --strip-components=1 -C /usr/local/bin/
    docker info
    docker version
  """)
}

void runMaven(String cmd) {
  sh ("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yamlFile '.ci/podSpecs/mavenNodeJSDindAgent.yml'
    }
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr:'50', artifactNumToKeepStr: '1'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Build') {
      steps {
        container('maven') {
          // prepare maven container
          installDockerBinaries()
          setupPermissionsForHostDirs('upgrade')

          runMaven('install -Pproduction -Dskip.docker -DskipTests -T\$LIMITS_CPU')
        }
      }
    }
    stage('Unit tests') {
      parallel {
        stage('Backend') {
          steps {
            container('maven') {
              runMaven('test -Dskip.fe.build -T\$LIMITS_CPU')
            }
          }
          post {
            always {
              junit testResults: '**/surefire-reports/**/*.xml', keepLongStdio: true
            }
          }
        }
        stage('Frontend') {
          steps {
            container('node') {
              sh ('''
                cd ./client
                yarn test:ci
              ''')
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
    stage('ITs against different engines') {
      environment {
        CAM_REGISTRY = credentials('repository-camunda-cloud')
      }
      parallel {
        stage('IT Latest') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-latest_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yamlFile '.ci/podSpecs/mavenNodeJSDindAgent.yml'
            }
          }
          steps {
            integrationTestSteps('latest')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveTestArtifacts('backend', 'latest')
            }
          }
        }
        stage('IT 7.9') {
          when {
            beforeAgent true
            branch 'master'
          }
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.9_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yamlFile '.ci/podSpecs/mavenNodeJSDindAgent.yml'
            }
          }
          steps {
            integrationTestSteps('7.9')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveTestArtifacts('backend', '7_9')
            }
          }
        }
        stage('IT 7.8') {
          when {
            beforeAgent true
            branch 'master'
          }
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.8_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yamlFile '.ci/podSpecs/mavenNodeJSDindAgent.yml'
            }
          }
          steps {
            integrationTestSteps('7.8')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveTestArtifacts('backend', '7_8')
            }
          }
        }
      }
    }
    stage('RESTAPI Docs') {
      steps {
        container('maven') {
          runMaven('clean package -Pdocs,production -DskipTests -f backend/pom.xml')
        }
      }
      post {
        success {
          archiveArtifacts artifacts: 'backend/target/docs/**/*.*'
        }
      }
    }
    stage('Deploy') {
      parallel {
        stage('Deploy to Nexus') {
          when {
            branch 'master'
          }
          steps {
            container('maven') {
              runMaven('deploy -Pproduction -Dskip.fe.build -DskipTests')
            }
          }
        }
        stage('Build Docker') {
          when {
            expression { BRANCH_NAME ==~ /(master|.*-deploy)/ }
          }
          environment {
            VERSION = readMavenPom().getVersion().replace('-SNAPSHOT', '')
            SNAPSHOT = readMavenPom().getVersion().contains('SNAPSHOT')
            IMAGE_TAG = getImageTag()
            GCR_REGISTRY = credentials('docker-registry-ci3')
          }
          steps {
            container('docker') {
              sh ("""
                echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin

                docker build -t ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG} \
                  --build-arg=SKIP_DOWNLOAD=true \
                  --build-arg=VERSION=${VERSION} \
                  --build-arg=SNAPSHOT=${SNAPSHOT} \
                  --build-arg=USERNAME=${NEXUS_USR} \
                  --build-arg=PASSWORD=${NEXUS_PSW} \
                  .

                docker push ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG}

                if [ "${env.BRANCH_NAME}" = 'master' ]; then
                  docker tag ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG} ${PROJECT_DOCKER_IMAGE()}:latest
                  docker push ${PROJECT_DOCKER_IMAGE()}:latest
                fi
              """)
            }
          }
        }
      }
    }
    stage ('Deploy to K8s') {
      when {
        expression { BRANCH_NAME ==~ /(.*-deploy)/ }
      }
      steps {
        build job: '/deploy-optimize-branch-to-k8s',
          parameters: [
              string(name: 'BRANCH', value: getBranchSlug()),
          ]
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
  }
}
