#!/usr/bin/env groovy

boolean slaveDisconnected() {
  return currentBuild.rawBuild.getLog(10000).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException).*/
}

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }
def static NODE_POOL() { return "slaves" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"


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
    - key: "slaves"
      operator: "Exists"
      effect: "NoSchedule"
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-cambpm-config
  imagePullSecrets:
  - name: registry-camunda-cloud-secret
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
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 3
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 6
        memory: 4Gi
      requests:
        cpu: 4
        memory: 4Gi
    """
}

String elasticSearchContainerSpec(def esVersion) {
  return """
  - name: elasticsearch-9200
    image: docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    resources:
      limits:
        cpu: 5
        memory: 4Gi
      requests:
        cpu: 5
        memory: 4Gi
    env:
      - name: ES_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
      - name: ES_JAVA_OPTS
        value: "-Xms2g -Xmx2g"
      - name: bootstrap.memory_lock
        value: true
      - name: discovery.type
        value: single-node
      - name: http.port
        value: 9200
      - name: cluster.name
        value: elasticsearch
   """
}

String camBpmContainerSpec(String camBpmVersion) {
  String camBpmDockerImage = getCamBpmDockerImage(camBpmVersion)
  return """
  - name: cambpm
    image: ${camBpmDockerImage}
    tty: true
    env:
      - name: JAVA_OPTS
        value: "-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 4
        memory: 3Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
    """
}

static String mavenAgent() {
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
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
"""
}

void integrationTestSteps(String camBpmVersion) {
  gitCheckoutOptimize()
  container('maven') {
    runMaven("verify -Dskip.docker -Pit,engine-${camBpmVersion} -pl backend -am -T\$LIMITS_CPU")
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
  return "registry.camunda.cloud/camunda-bpm-platform-ee:${camBpmVersion}"
}

void gitCheckoutOptimize() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenAgent()
    }
  }

  environment {
    CAM_REGISTRY = credentials('repository-camunda-cloud')
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
  }
  
  stages {
    stage("Prepare") {
      agent none
      steps {
        gitCheckoutOptimize()
        script {
          env.ES_VERSION = readMavenPom().getProperties().getProperty(ES_TEST_VERSION_POM_PROPERTY)
        }
      }
    }
    stage('IT') {
      failFast false
      parallel {
        stage('IT 7.10') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.10_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec('7.10.6', env.ES_VERSION)
            }
          }
          steps {
            integrationTestSteps('7.10')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT 7.11') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.11_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml integrationTestPodSpec('7.11.0', env.ES_VERSION)
            }
          }
          steps {
            integrationTestSteps('7.11')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
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
