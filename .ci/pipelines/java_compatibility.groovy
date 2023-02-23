#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
def static OPENJDK_MAVEN_DOCKER_IMAGE(String imageTagSuffix) { return "maven:3.8.1-${imageTagSuffix}" }
def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}" }
def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) { return "docker.elastic.co/elasticsearch/elasticsearch:${esVersion}" }

static String mavenIntegrationTestAgent(mavenImage, esVersion, cambpmVersion) {
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
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci/deployments/optimize
      name: ci-optimize-cambpm-config
  - name: docker-storage
    emptyDir: {}
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
  containers:
  - name: gcloud
    image: gcr.io/google.com/cloudsdktool/cloud-sdk:slim
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
  - name: maven
    image: ${mavenImage}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 6
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: 6
        memory: 20Gi
      requests:
        cpu: 6
        memory: 20Gi
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    imagePullPolicy: Always
    env:
      - name: JAVA_OPTS
        value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms2g -Xmx2g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: http.port
      value: 9200
    - name: bootstrap.memory_lock
      value: true
    # We usually run our integration tests concurrently, as some cleanup methods like #deleteAllOptimizeData
    # internally make usage of scroll contexts this lead to hits on the scroll limit.
    # Thus this increased scroll context limit.
    - name: search.max_open_scroll_context
      value: 1000
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
  - name: docker
    image: docker:20.10.5-dind
    args:
      - --storage-driver
      - overlay2
      - --ipv6
      - --fixed-cidr-v6
      - "2001:db8:1::/64"
    env:
      # The new dind versions expect secure access using cert
      # Setting DOCKER_TLS_CERTDIR to empty string will disable the secure access
      # (see https://hub.docker.com/_/docker?tab=description&page=1)
      - name: DOCKER_TLS_CERTDIR
        value: ""
    securityContext:
      privileged: true
    volumeMounts:
      - mountPath: /var/lib/docker
        name: docker-storage
    tty: true
    resources:
      limits:
        cpu: 12
        memory: 16Gi
      requests:
        cpu: 12
        memory: 16Gi
"""
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

void integrationTestSteps() {
  optimizeCloneGitRepo(params.BRANCH)
  container('maven') {
    runMaven("test -Dskip.fe.build");
    runMaven("verify -Dskip.docker -Pit,engine-latest -pl backend,upgrade,util/optimize-reimport-preparation -am")
  }
}

pipeline {
  agent none
  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml plainMavenAgent(NODE_POOL(), OPENJDK_MAVEN_DOCKER_IMAGE("jdk-11-slim"))
        }
      }
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage('Java Integration Tests') {
      failFast false
      parallel {
        stage("OpenJDK 11 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-JDK11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent(OPENJDK_MAVEN_DOCKER_IMAGE("openjdk-11-slim"), "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_es-JDK11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_jdk11.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_jdk11.log', onlyIfSuccessful: false
            }
          }
        }
        stage("OpenJDK 16 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-JDK16_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent(OPENJDK_MAVEN_DOCKER_IMAGE("openjdk-16-slim"), "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_es-JDK16_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_jdk16.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_jdk16.log', onlyIfSuccessful: false
            }
          }
        }
        stage("Adopt Open JDK 11 Integration") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_es-ADOPT11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenIntegrationTestAgent("adoptopenjdk/maven-openjdk11:latest", "${env.ES_VERSION}", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_es-ADOPT11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_ADOP.log'
              }
                archiveArtifacts artifacts: 'elasticsearch_ADOP.log', onlyIfSuccessful: false
            }
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
      retriggerBuildIfDisconnected()
    }
  }
}
