#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

boolean slaveDisconnected() {
  return currentBuild.rawBuild.getLog(500).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException).*/
}

String storeNumOfBuilds() {
  return env.BRANCH_NAME == 'master' ? '30' : '10'
}

String storeNumOfArtifacts() {
  return env.BRANCH_NAME == 'master' ? '5' : '1'
}

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

def static PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }

CAMBPM_VERSION_LATEST = "7.11.0"


String getCamBpmDockerImage(String camBpmVersion) {
  return "registry.camunda.cloud/camunda-bpm-platform-ee:${camBpmVersion}"
}

/************************ START OF PIPELINE ***********************/

String basePodSpec() {
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  tolerations:
    - key: "slaves"
      operator: "Exists"
      effect: "NoSchedule"
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-cambpm-config
  - name: es-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-es-config
  imagePullSecrets:
  - name: registry-camunda-cloud-secret
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
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
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
    """
}

String camBpmContainerSpec(String camBpmVersion = CAMBPM_VERSION_LATEST) {
  String camBpmDockerImage = getCamBpmDockerImage(camBpmVersion)
  return """
  - name: cambpm
    image: ${camBpmDockerImage}
    tty: true
    env:
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
      - name: JAVA_OPTS
        value: "-Xms512m -Xmx512m -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 2
        memory: 1Gi
      requests:
        cpu: 2
        memory: 1Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
    """
}

String elasticSearchContainerSpec(boolean ssl = false, boolean basicAuth = false, httpPort = "9200") {
  String basicAuthConfig = (basicAuth) ? """
      - name: ELASTIC_PASSWORD
        value: optimize
        """ : ""
  String imageName = (basicAuth) ? "elasticsearch-platinum" : "elasticsearch"
  String sslConfig = (ssl) ? """
      - name: xpack.security.http.ssl.enabled
        value: true
      - name: xpack.security.http.ssl.certificate_authorities
        value: /usr/share/elasticsearch/config/certs/ca/ca.crt
      - name: xpack.security.http.ssl.certificate
        value: /usr/share/elasticsearch/config/certs/optimize/optimize.crt
      - name: xpack.security.http.ssl.key
        value: /usr/share/elasticsearch/config/certs/optimize/optimize.key
    volumeMounts:
      - name: es-config
        mountPath: /usr/share/elasticsearch/config/certs/ca/ca.crt
        subPath: ca.crt
      - name: es-config
        mountPath: /usr/share/elasticsearch/config/certs/optimize/optimize.crt
        subPath: optimize.crt
      - name: es-config
        mountPath: /usr/share/elasticsearch/config/certs/optimize/optimize.key
        subPath: optimize.key
    """ : ""
  return """
  - name: elasticsearch-${httpPort}
    image: docker.elastic.co/elasticsearch/${imageName}:6.2.0
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      limits:
        cpu: 2
        memory: 2Gi
      requests:
        cpu: 2
        memory: 1Gi
    env:
      - name: ES_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
      - name: ES_JAVA_OPTS
        value: "-Xms512m -Xmx512m"
      - name: bootstrap.memory_lock
        value: true
      - name: discovery.type
        value: single-node
      - name: http.port
        value: ${httpPort}
      - name: cluster.name
        value: elasticsearch
   """ + basicAuthConfig + sslConfig
}

String integrationTestPodSpec(String camBpmVersion = CAMBPM_VERSION_LATEST) {
  return basePodSpec() + camBpmContainerSpec(camBpmVersion) + elasticSearchContainerSpec()
}

 String securityTestPodSpec() {
  esConfigBasicAuthAndSsl = elasticSearchContainerSpec(true, true, 9200)
  esConfigSsl = elasticSearchContainerSpec(true, false, 9201)
  esConfigBasicAuth = elasticSearchContainerSpec(false, true, 9202)
  return basePodSpec() + camBpmContainerSpec() + esConfigBasicAuthAndSsl + esConfigSsl + esConfigBasicAuth
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yamlFile '.ci/podSpecs/builderAgent.yml'
    }
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: storeNumOfBuilds(), artifactNumToKeepStr: storeNumOfArtifacts()))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Build') {
      environment {
        VERSION = readMavenPom().getVersion()
      }
      steps {
        retry(2) {
          container('maven') {
            runMaven('install -Pproduction,docs,engine-latest -Dskip.docker -DskipTests -T\$LIMITS_CPU')
          }
          stash name: "optimize-stash-client", includes: "client/build/**"
          stash name: "optimize-stash-distro", includes: "m2-repository/org/camunda/optimize/camunda-optimize/*${VERSION}/*-production.tar.gz,m2-repository/org/camunda/optimize/camunda-optimize/*${VERSION}/*.xml,m2-repository/org/camunda/optimize/camunda-optimize/*${VERSION}/*.pom"
        }
      }
      post {
        success {
          archiveArtifacts artifacts: 'backend/target/docs/**/*.*'
        }
      }
    }
    stage('Unit tests') {
      parallel {
        stage('Backend') {
          steps {
            retry(2) {
              container('maven') {
                runMaven('test -Dskip.fe.build -Dskip.docker -T\$LIMITS_CPU')
              }
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
            retry(2) {
              container('node') {
                sh('''
                  cd ./client
                  yarn run eslint src/
                  yarn test:ci
                ''')
              }
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
    stage('Integration and Migration tests') {
      environment {
        CAM_REGISTRY = credentials('repository-camunda-cloud')
      }
      failFast true
      parallel {
        stage('Migration') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-migration_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec()
            }
          }
          steps {
            retry(2) {
              unstash name: "optimize-stash-distro"
              unstash name: "optimize-stash-client"
              migrationTestSteps()
            }
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('Rolling data upgrade') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-data-upgrade_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec()
            }
          }
          steps {
            retry(2) {
              unstash name: "optimize-stash-distro"
              dataUpgradeTestSteps()
            }
          }
        }
        stage('Security') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-security_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml securityTestPodSpec()
            }
          }
          steps {
            retry(2) {
              securityTestSteps()
            }
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT Latest') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-latest_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec()
            }
          }
          steps {
            retry(2) {
              unstash name: "optimize-stash-client"
              integrationTestSteps('latest')
            }
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT 7.9') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.9_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec('7.9.12')
            }
          }
          steps {
            retry(2) {
              unstash name: "optimize-stash-client"
              integrationTestSteps('7.9')
            }
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT 7.10') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.10_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec('7.10.6')
            }
          }
          steps {
            retry(2) {
              unstash name: "optimize-stash-client"
              integrationTestSteps('7.10')
            }
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
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
            retry(2) {
              container('maven') {
                runMaven('deploy -Dskip.fe.build -DskipTests -Dskip.docker')
              }
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
            retry(2) {
              container('docker') {
                sh ("""
                  echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin

                  docker build -t ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG} \
                    --build-arg SKIP_DOWNLOAD=true \
                    --build-arg VERSION=${VERSION} \
                    --build-arg SNAPSHOT=${SNAPSHOT} \
                    --build-arg USERNAME="${NEXUS_USR}" \
                    --build-arg PASSWORD="${NEXUS_PSW}" \
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
      // Do not send email if the slave disconnected
      script {
        if (!slaveDisconnected()){
          buildNotification(currentBuild.result)
        }
      }
    }
    always {
      // Retrigger the build if the slave disconnected
      script {
        if (slaveDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

/************************ END OF PIPELINE ***********************/

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
    runMaven("verify -Dskip.docker -Dskip.fe.build -Pproduction,it,engine-${engineVersion} -pl backend -am -T\$LIMITS_CPU")
  }
}

void securityTestSteps() {
  container('maven') {
    // run migration tests
    runMaven("verify -Dskip.docker -Dskip.fe.build -pl qa/connect-to-secured-es-tests -am -Psecured-es-it")
  }
}

void migrationTestSteps() {
  container('maven') {
    sh ("""apt-get update && apt-get install -y jq netcat""")
    runMaven("install -Dskip.docker -Dskip.fe.build -DskipTests -pl backend -am -Pproduction,engine-latest,it")
    runMaven("install -Dskip.docker -DskipTests -f qa")
    runMaven("verify -Dskip.docker -Dskip.fe.build -pl upgrade")
    runMaven("verify -Dskip.docker -Dskip.fe.build -pl qa/upgrade-es-schema-tests -Pupgrade-es-schema-tests")
    runMaven("verify -Dskip.docker -Dskip.fe.build -pl util/optimize-reimport-preparation -Pit")
  }
}

void dataUpgradeTestSteps() {
  container('maven') {
    sh ("""apt-get update && apt-get install -y jq""")
    runMaven("install -Dskip.docker -Dskip.fe.build -DskipTests -pl backend,qa/data-generation -am -Pproduction,engine-7.10")
    runMaven("install -Dskip.docker -Dskip.fe.build -DskipTests -f qa/upgrade-optimize-data -pl generator -am")
    runMaven("verify -Dskip.docker -Dskip.fe.build -f qa/upgrade-optimize-data -am -Pupgrade-optimize-data")
  }
}

void runMaven(String cmd) {
  sh ("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}
