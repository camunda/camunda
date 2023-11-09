#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.5-openjdk-17-slim" }

def static CAMBPM_DOCKER_IMAGE(String camBpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${camBpmVersion}"
}

def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) {
  return "docker.elastic.co/elasticsearch/elasticsearch:${esVersion}"
}

static String mavenElasticsearchIntegrationTestAgent(esVersion, camBpmVersion) {
  return itStageBasePod() + camBpmContainerSpec(camBpmVersion) + elasticSearchContainerSpec(esVersion)
}

static String mavenElasticsearchIntegrationTestAgentWithoutCambpm(esVersion) {
  return itStageBasePod() + elasticSearchContainerSpec(esVersion)
}

static String itStageBasePod() {
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
    image: ${MAVEN_DOCKER_IMAGE()}
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

static String camBpmContainerSpec(String camBpmVersion) {
  return """
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(camBpmVersion)}
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
    """
}

static String elasticSearchContainerSpec(esVersion) {
  return """
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
    - name: xpack.security.enabled
      value: false
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
  """
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

void integrationTestSteps(String excludedGroups = '', String includedGroups = '', String profiles = 'it,engine-latest') {
  optimizeCloneGitRepo(params.BRANCH)
  container('maven') {
    runMaven("verify -Dit.test.excludedGroups=${excludedGroups} -Dit.test.includedGroups=${includedGroups} -Dskip.docker -Dskip.fe.build -P${profiles} -pl backend -am -T\$LIMITS_CPU")
  }
}

pipeline {
  agent none
  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timestamps()
    timeout(time: 240, unit: 'MINUTES')
  }

  stages {
    stage("Prepare") {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml plainMavenAgent(NODE_POOL(), MAVEN_DOCKER_IMAGE())
        }
      }
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage('Elasticsearch Integration Tests') {
      failFast false
      parallel {
        stage("ES 8.8.0 EBP & Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("8.8.0", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('', 'import,eventBasedProcess')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_ebpimport.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_ebpimport.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 Report Evaluation IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("8.8.0", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('', 'reportEvaluation')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_reportevaluation.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_reportevaluation.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 C8 Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880c8import_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgentWithoutCambpm("8.8.0")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880c8import_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('', 'ccsm-test', 'ccsm-it')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_zeebe.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_zeebe.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("8.8.0", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('ccsm-test,import,eventBasedProcess,reportEvaluation', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_it.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_it.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 EBP & Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("8.8.0", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('', 'import,eventBasedProcess')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_ebpimport.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_ebpimport.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 Report Evaluation IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("8.8.0", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('', 'reportEvaluation')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearchpd_880_reportevaluation.log'
              }
              archiveArtifacts artifacts: 'elasticsearchpd_880_reportevaluation.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 C8 Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880c8import_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgentWithoutCambpm("8.8.0")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880c8import_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('', 'ccsm-test', 'ccsm-it')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_zeebe.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_zeebe.log', onlyIfSuccessful: false
            }
          }
        }
        stage("ES 8.8.0 IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_880it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml mavenElasticsearchIntegrationTestAgent("8.8.0", "${env.CAMBPM_VERSION}")
            }
          }
          environment {
            LABEL = "optimize-ci-build_880it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('ccsm-test,import,eventBasedProcess,reportEvaluation', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: false
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch_880_it.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_880_it.log', onlyIfSuccessful: false
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
