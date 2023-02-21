#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-physsd-stable" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }
def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) { return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}" }
def static ELASTICSEARCH_DOCKER_IMAGE(String esVersion) { return "docker.elastic.co/elasticsearch/elasticsearch:${esVersion}" }

static String queryPerformanceConfig(esVersion, camBpmVersion) {
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
  securityContext:
    fsGroup: 1000
  volumes:
    - name: ssd-storage
      hostPath:
        path: /mnt/disks/array0
        type: Directory
    - name: cambpm-invoice-override
      emptyDir: {}
  initContainers:
  - name: init-sysctl
    image: ${MAVEN_DOCKER_IMAGE()}
    command:
    - sysctl
    - -w
    - vm.max_map_count=262144
    securityContext:
      privileged: true
  - name: cleanup
    image: busybox
    imagePullPolicy: Always
    command: ["rm", "-fr", "/data/*"]
    volumeMounts:
      - name: ssd-storage
        mountPath: /data
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
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      # Note: high cpu request here to ensure this pod is deployed on a dedicated node, with an exclusive ssd
      # this is 30 - (cpu of other containers)
      limits:
        cpu: 1
        memory: 6Gi
      requests:
        cpu: 1
        memory: 6Gi
    volumeMounts:
      - name: ssd-storage
        mountPath: /ssd-storage
""" \
 + gcloudContainerSpec() \
 + postgresContainerSpec() + camBpmContainerSpec(camBpmVersion) \
 + elasticSearchContainerSpec(esVersion)
}

static String gcloudContainerSpec() {
  return """
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
    volumeMounts:
      - name: ssd-storage
        mountPath: /db_dump
        subPath: db-dump
  """
}

static String postgresContainerSpec() {
  return """
  - name: postgresql
    image: postgres:11.2
    env:
      - name: POSTGRES_USER
        value: camunda
      - name: POSTGRES_PASSWORD
        value: camunda
      - name: POSTGRES_DB
        value: engine
    resources:
      limits:
        cpu: 4
        memory: 6Gi
      requests:
        cpu: 4
        memory: 6Gi
    volumeMounts:
      - name: ssd-storage
        mountPath: /var/lib/postgresql/data
        subPath: pg-data
      - name: ssd-storage
        mountPath: /db_dump
        subPath: db-dump
  """
}

static String camBpmContainerSpec(String camBpmVersion) {
  return """
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(camBpmVersion)}
    imagePullPolicy: Always
    tty: true
    env:
      - name: JAVA_OPTS
        value: "-Xms4g -Xmx4g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
      - name: DB_DRIVER
        value: "org.postgresql.Driver"
      - name: DB_USERNAME
        value: "camunda"
      - name: DB_PASSWORD
        value: "camunda"
      - name: DB_URL
        value: "jdbc:postgresql://localhost:5432/engine"
      - name: WAIT_FOR
        value: localhost:5432
    resources:
      limits:
        cpu: 8
        memory: 6Gi
      requests:
        cpu: 8
        memory: 6Gi
    volumeMounts:
      - name: cambpm-invoice-override
        mountPath: /camunda/webapps/camunda-invoice
    """
}

static String elasticSearchContainerSpec(esVersion) {
  return """
  - name: elasticsearch
    image: ${ELASTICSEARCH_DOCKER_IMAGE(esVersion)}
    env:
    - name: ES_JAVA_OPTS
      value: "-Xms8g -Xmx8g"
    - name: cluster.name
      value: elasticsearch
    - name: discovery.type
      value: single-node
    - name: bootstrap.memory_lock
      value: true
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      requests:
        cpu: 16
        memory: 16Gi
      limits:
        cpu: 16
        memory: 16Gi
    volumeMounts:
      - name: ssd-storage
        mountPath: /usr/share/elasticsearch/data
        subPath: es-data
      - name: ssd-storage
        mountPath: /usr/share/elasticsearch/logs
        subPath: es-logs
  """
}

pipeline {
  agent none

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 3, unit: 'DAYS')
  }

  stages {
    stage('Prepare') {
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
    stage('Query Performance') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build-${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml queryPerformanceConfig(env.ES_VERSION, env.CAMBPM_VERSION)
        }
      }
      environment {
        LABEL = "optimize-ci-build-${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
      }
      stages {
        stage('Build') {
          steps {
            optimizeCloneGitRepo(params.BRANCH)
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                sh 'mvn -T\$LIMITS_CPU -DskipTests -Dskip.fe.build -Dskip.docker -s $MAVEN_SETTINGS_XML clean install -B'
              }
            }
          }
        }
        stage('Restore Test Data') {
          steps {
            timeout(360) {
              container('gcloud') {
                sh "gsutil -q cp gs://optimize-data/${SQL_DUMP} /db_dump/${SQL_DUMP}"
              }
              container('postgresql') {
                sh "pg_restore --clean --if-exists -v -h localhost -U camunda -d engine /db_dump/${SQL_DUMP}"
              }
            }
          }
          post {
            always {
              container('gcloud') {
                sh "rm -f /db_dump/${SQL_DUMP}"
              }
            }
          }
        }
        stage('Run Query Performance Tests') {
          steps {
            container('maven') {
              configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                sh 'mvn -f qa/query-performance-tests/pom.xml -s $MAVEN_SETTINGS_XML clean verify -Pquery-performance-tests -B'
              }
            }
          }
          post {
            always {
              container('maven') {
                sh 'curl localhost:9200/_cat/indices?v'
                junit testResults: 'qa/query-performance-tests/target/surefire-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
              }
              container('gcloud') {
                sh 'apt-get install kubectl'
                sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch.log'
                archiveArtifacts artifacts: 'elasticsearch.log', onlyIfSuccessful: false
              }
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
