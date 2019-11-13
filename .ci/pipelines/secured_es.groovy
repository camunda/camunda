#!/usr/bin/env groovy

// general properties for CI execution
def static NODE_POOL() { return "slaves" }

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

String getCamBpmDockerImage(String camBpmVersion) {
    return "registry.camunda.cloud/camunda-bpm-platform-ee:${camBpmVersion}"
}

String basePodSpec() {
    return """
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

String elasticSearchContainerSpec(boolean ssl = false, boolean basicAuth = false, httpPort = "9200", def esVersion) {
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
    image: docker.elastic.co/elasticsearch/${imageName}:${esVersion}
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
        value: ${httpPort}
      - name: cluster.name
        value: elasticsearch
   """ + basicAuthConfig + sslConfig
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
    image: maven:3.6.1-jdk-8-slim
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

String securityTestPodSpec(def esVersion, def camBpmVersion) {
    def esConfigBasicAuthAndSsl = elasticSearchContainerSpec(true, true, 9200, esVersion)
    def esConfigSsl = elasticSearchContainerSpec(true, false, 9201, esVersion)
    def esConfigBasicAuth = elasticSearchContainerSpec(false, true, 9202, esVersion)
    return basePodSpec() + camBpmContainerSpec(camBpmVersion) + esConfigBasicAuthAndSsl + esConfigSsl + esConfigBasicAuth
}

pipeline {
    agent none
    environment {
        NEXUS = credentials("camunda-nexus")
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '50'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage("Prepare") {
            agent {
                kubernetes {
                    cloud 'optimize-ci'
                    label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
                    defaultContainer 'jnlp'
                    yaml mavenAgent()
                }
            }
            steps {
                cloneGitRepo()
                script {
                    def mavenProps = readMavenPom().getProperties()
                    env.ES_VERSION = mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
                    env.CAMBPM_VERSION = mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
                }
            }
        }
        stage('Security') {
            agent {
                kubernetes {
                    cloud 'optimize-ci'
                    label "optimize-ci-build-it-security_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
                    defaultContainer 'jnlp'
                    yaml securityTestPodSpec(env.ES_VERSION, env.CAMBPM_VERSION)
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
    }
}

void securityTestSteps() {
    cloneGitRepo()
    container('maven') {
        // run migration tests
        runMaven("verify -Dskip.docker -Dskip.fe.build -pl qa/connect-to-secured-es-tests -am -Psecured-es-it")
    }
}

private void cloneGitRepo() {
    git url: 'git@github.com:camunda/camunda-optimize',
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
}

void runMaven(String cmd) {
    sh("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}