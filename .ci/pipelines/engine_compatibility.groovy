#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.5-openjdk-17-slim" }

def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

String basePodSpec() {
  return """
apiVersion: v1
kind: Pod
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
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci/deployments/optimize
      name: ci-optimize-cambpm-config
  imagePullSecrets:
  - name: registry-camunda-cloud
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
        capabilities:
          add: ["IPC_LOCK", "SYS_RESOURCE"]
    - name: increase-the-ulimit
      image: busybox
      command: ["sh", "-c", "ulimit -n 65536"]
      securityContext:
        privileged: true
        capabilities:
          add: ["IPC_LOCK", "SYS_RESOURCE"]
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
    resources:
      limits:
        cpu: 6
        memory: 20Gi
      requests:
        cpu: 6
        memory: 20Gi
    """
}

String elasticSearchContainerSpec(def esVersion) {
  return """
  - name: elasticsearch-9200
    image: docker.elastic.co/elasticsearch/elasticsearch:${esVersion}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms2g -Xmx2g"
    - name: cluster.name
      value: elasticsearch
    - name: http.port
      value: 9200
    - name: discovery.type
      value: single-node
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
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    resources:
      limits:
        cpu: 4
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
   """
}

String camBpmContainerSpec(String camBpmVersion) {
  String camBpmDockerImage = getCamBpmDockerImage(camBpmVersion)
  return """
  - name: cambpm
    image: ${camBpmDockerImage}
    imagePullPolicy: Always
    tty: true
    env:
      - name: JAVA_OPTS
        value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 2Gi
      requests:
        cpu: 4
        memory: 2Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
    """
}

void integrationTestSteps(String camBpmVersion, String excludedGroups = '', String includedGroups = '') {
  optimizeCloneGitRepo(params.BRANCH)
  container('maven') {
    runMaven("verify -Dit.test.excludedGroups=${excludedGroups} -Dit.test.includedGroups=${includedGroups} -Dskip.docker -Dskip.fe.build -Pit,engine-${camBpmVersion} -pl backend -am -T\$LIMITS_CPU")
  }
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

String integrationTestPodSpec(String camBpmVersion, def esVersion) {
  return basePodSpec() + camBpmContainerSpec(camBpmVersion) + elasticSearchContainerSpec(esVersion)
}

String getCamBpmDockerImage(String camBpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${camBpmVersion}"
}

pipeline {
  agent none
  environment {
    CAM_REGISTRY = credentials('repository-camunda-cloud')
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
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
        setCamBpmSnapshotVersion()
        script {
          env.CAMBPM_7_18_VERSION = getCamBpmVersion('engine-7.18')
          env.CAMBPM_7_19_VERSION = getCamBpmVersion('engine-7.19')
          env.CAMBPM_7_20_VERSION = getCamBpmVersion('engine-7.20')
          env.ES8_VERSION = '8.8.0'
        }
      }
    }
    stage('IT') {
      failFast false
      parallel {
        stage("CamBPM 7.18 EBP & Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_718ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_18_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_718ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.18', '', 'import,eventBasedProcess')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_718_ebpimport.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_718_ebpimport.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.18 Report Evaluation IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_718repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_18_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_718repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.18', '', 'reportEvaluation')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_718_reportevaluation.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_718_reportevaluation.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.18 IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_718it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_18_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_718it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.18', 'ccsm-test,import,eventBasedProcess,reportEvaluation', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_718_it.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_718_it.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.19 Integration EBP & Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_719ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_19_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_719ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.19', '', 'import,eventBasedProcess')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_719_ebpimport.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_719_ebpimport.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.19 Integration Report Evaluation IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_719repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_19_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_719repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.19', '', 'reportEvaluation')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_719_reportevaluation.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_719_reportevaluation.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.19 IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_719it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_19_VERSION,  env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_719it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.19', 'ccsm-test,import,eventBasedProcess,reportEvaluation', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_719_it.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_719_it.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.20 Integration EBP & Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_720ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_20_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_720ebpimp_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.20', '', 'import,eventBasedProcess')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_720_ebpimport.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_720_ebpimport.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.20 Integration Report Evaluation IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_720repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_20_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_720repev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.20', '', 'reportEvaluation')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_720_reportevaluation.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_720_reportevaluation.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM 7.20 IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_720it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_7_20_VERSION,  env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_720it_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('7.20', 'ccsm-test,import,eventBasedProcess,reportEvaluation', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_720_it.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_720_it.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM SNAPSHOT EBP & Import IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_snapebpimport_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_SNAPSHOT_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_snapebpimport_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('snapshot', '', 'import,eventBasedProcess')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_snapshot_ebpmimport.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_snapshot_ebpmimport.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM SNAPSHOT Integration Report Evaluation IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_snaprepev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_SNAPSHOT_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "ooptimize-ci-build_snaprepev_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('snapshot', '', 'reportEvaluation')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_snapshot_reportevaluation.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_snapshot_reportevaluation.log', onlyIfSuccessful: false
            }
          }
        }
        stage("CamBPM SNAPSHOT IT") {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build_snapit_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec(env.CAMBPM_SNAPSHOT_VERSION, env.ES8_VERSION)
            }
          }
          environment {
            LABEL = "optimize-ci-build_snapit_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          }
          steps {
            integrationTestSteps('snapshot', 'ccsm-test,import,eventBasedProcess,reportEvaluation', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch-9200 > elasticsearch_snapshot_it.log'
              }
              archiveArtifacts artifacts: 'elasticsearch_snapshot_it.log', onlyIfSuccessful: false
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

private void getCamBpmVersion(String profileId) {
  def profile = readMavenPom().getProfiles().find { it.getId().equals(profileId) }
  return profile.getProperties().getProperty("camunda.engine.version")
}
