#!/usr/bin/env groovy

static String agent() {
  return """
---
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  tolerations:
    - key: "slaves"
      operator: "Exists"
      effect: "NoSchedule"
  initContainers:
    - name: init-sysctl
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.1
      command:
      - "sh"
      args:
      - "-c"
      - "sysctl -w vm.max_map_count=262144 && \
         cp -r /usr/share/elasticsearch/config/* /usr/share/elasticsearch/config_new/"
      securityContext:
        privileged: true
      volumeMounts:
      - mountPath: /usr/share/elasticsearch/config_new/
        name: configdir
    - name: init-plugins
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.1
      command:
      - "sh"
      args:
      - "-c"
      - "elasticsearch-plugin install --batch repository-gcs && \
        elasticsearch-keystore create && \
        elasticsearch-keystore add-file gcs.client.operate_ci_service_account.credentials_file /usr/share/elasticsearch/svc/operate-ci-service-account.json"
      securityContext:
        privileged: true
        capabilities:
          add:
            - IPC_LOCK
      volumeMounts:
      - mountPath: /usr/share/elasticsearch/config/
        name: configdir
      - mountPath: /usr/share/elasticsearch/plugins/
        name: plugindir
      - mountPath: /usr/share/elasticsearch/svc/
        name: operate-ci-service-account
        readOnly: true
  containers:
    - name: maven
      image: maven:3.6.1-jdk-11
      command: ["cat"]
      tty: true
      env:
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
    - name: elasticsearch
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.1
      env:
        - name: ES_JAVA_OPTS
          value: '-Xms4g -Xmx4g'
        - name: cluster.name
          value: docker-cluster
        - name: discovery.type
          value: single-node
        - name: action.auto_create_index
          value: "true"
        - name: bootstrap.memory_lock
          value: "true"
      livenessProbe:
        httpGet:
          path: /_cluster/health?local=true
          port: es-http
        initialDelaySeconds: 10
      readinessProbe:
        httpGet:
          path: /_cluster/health?local=true
          port: es-http
        initialDelaySeconds: 10
      securityContext:
        privileged: true
        capabilities:
          add:
            - IPC_LOCK
      volumeMounts:
      - mountPath: /usr/share/elasticsearch/config/
        name: configdir
      - mountPath: /usr/share/elasticsearch/plugins/
        name: plugindir
      ports:
        - containerPort: 9200
          name: es-http
          protocol: TCP
        - containerPort: 9300
          name: es-transport
          protocol: TCP
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
    - name: zeebe
      image: camunda/zeebe:0.22.0-alpha2
      env:
      volumeMounts:
        - name: zeebe-configuration
          mountPath: /usr/local/zeebe/conf/zeebe.cfg.toml
          subPath: zeebe.cfg.toml
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
  volumes:
  - name: configdir
    emptyDir: {}
  - name: plugindir
    emptyDir: {}
  - name: operate-ci-service-account
    secret:
      secretName: operate-ci-service-account
  - name: zeebe-configuration
    configMap:
      name: zeebe-configuration
      items:
      - key: zeebe.cfg.toml
        path: zeebe.cfg.toml
"""
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      //yamlFile '.ci/podSpecs/dataGeneratorAgent.yml'
      yaml agent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 2, unit: 'HOURS')
  }

  stages {
    stage('Prepare') {
      parallel {
        stage('Build Operate') {
          steps {
            git url: 'git@github.com:camunda/camunda-operate',
                branch: "master",
                credentialsId: 'camunda-jenkins-github-ssh',
                poll: false
            container('maven') {
              // Compile Operate and skip tests
              configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                sh ('mvn -B -s $MAVEN_SETTINGS_XML -DskipTests -P skipFrontendBuild clean install')
              }
            }
          }
        }
        stage('Import ES Snapshot') {
          steps {
            container('maven') {
                // Create repository
                sh ("""
                    echo \$(curl -sq -H "Content-Type: application/json" -d '{ "type": "gcs", "settings": { "bucket": "operate-data", "client": "operate_ci_service_account" }}' -XPUT "http://localhost:9200/_snapshot/my_gcs_repository")
                """)
                // Restore Snapshot
                sh ("""
                    echo \$(curl -sq -XPOST 'http://localhost:9200/_snapshot/my_gcs_repository/snapshot_1/_restore?wait_for_completion=true')
                """)
            }
          }
        }
      }
    }
    stage('Run performance tests') {
      steps {
        container('maven') {
          // Generate Data
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh ('mvn -B -s $MAVEN_SETTINGS_XML -f qa/import-performance-tests -P -docker,-skipTests verify')
          }
        }
      }
    }
    stage('Upload snapshot') {
      steps {
        container('maven') {
          //Delete Zeebe indices
          sh ("""
                echo \$(curl -sq -H "Content-Type: application/json" -XDELETE "http://localhost:9200/zeebe-record*")
            """)
          // Create repository
          sh ("""
                echo \$(curl -qs -H "Content-Type: application/json" -d '{ "type": "gcs", "settings": { "bucket": "operate-data", "client": "operate_ci_service_account" }}' -XPUT "http://localhost:9200/_snapshot/my_gcs_repository")
            """)
          // Delete previous Snapshot
          sh ("""
                echo \$(curl -qs -XDELETE "http://localhost:9200/_snapshot/my_gcs_repository/snapshot_2")
            """)
          // Trigger Snapshot
          sh ("""
                echo \$(curl -qs -XPUT "http://localhost:9200/_snapshot/my_gcs_repository/snapshot_2?wait_for_completion=true")
            """)
        }
      }
    }
  }

  post {
    failure {
      script{
        def notification = load "../.ci/pipelines/build_notification.groovy"
        notification.buildNotification(currentBuild.result)
      }
    }
  }
}
