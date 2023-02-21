#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }

def static NODE_POOL() { return "agents-n1-standard-8-netssd-stable" }

String gCloudAndMavenAgent() {
  """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  serviceAccountName: ci-optimize-camunda-cloud
  tolerations:
   - key: "${NODE_POOL()}"
     operator: "Exists"
     effect: "NoSchedule"
  imagePullSecrets:
    - name: registry-camunda-cloud
  initContainers: 
    - name: init-sysctl
      image: docker.elastic.co/elasticsearch/elasticsearch:${params.ES_VERSION}
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
      image: docker.elastic.co/elasticsearch/elasticsearch:${params.ES_VERSION}
      command:
      - "sh"
      args:
      - "-c"
      - "elasticsearch-plugin install --batch repository-gcs && \
        elasticsearch-keystore create && \
        elasticsearch-keystore add-file gcs.client.optimize_ci_service_account.credentials_file /usr/share/elasticsearch/svc/ci-service-account"
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
        name: ci-service-account
        readOnly: true
    - name: data-cleanup
      image: busybox
      imagePullPolicy: Always
      command: ["/bin/sh","-c"]
      args: ["rm -fr /usr/share/elasticsearch/data/* && rm -fr /usr/share/elasticsearch/logs/*"]
      securityContext:
        privileged: true
      volumeMounts:
        - name: ssd-storage
          mountPath: /usr/share/elasticsearch/data
          subPath: data
        - name: ssd-storage
          mountPath: /usr/share/elasticsearch/logs
          subPath: logs
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
      image:  ${MAVEN_DOCKER_IMAGE()}
      command: ["cat"]
      tty: true
      env:
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
      resources:
        limits:
          cpu: 2
          memory: 8Gi
        requests:
          cpu: 2
          memory: 8Gi
    - name: elasticsearch
      image: docker.elastic.co/elasticsearch/elasticsearch:${params.ES_VERSION}
      env:
        - name: ES_JAVA_OPTS
          value: '-Xms8g -Xmx8g'
        - name: cluster.name
          value: docker-cluster
        - name: discovery.type
          value: single-node
        - name: action.auto_create_index
          value: "false"
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
      - name: ssd-storage
        mountPath: /usr/share/elasticsearch/data
        subPath: data
      - name: ssd-storage
        mountPath: /usr/share/elasticsearch/logs
        subPath: logs
      ports:
        - containerPort: 9200
          name: es-http
          protocol: TCP
        - containerPort: 9300
          name: es-transport
          protocol: TCP
      resources:
        limits:
          cpu: 3
          memory: 16Gi
        requests:
          cpu: 3
          memory: 16Gi
  volumes:
  - name: configdir
    emptyDir: {}
  - name: plugindir
    emptyDir: {}
  - name: ci-service-account
    secret: 
      secretName: ci-service-account
  - name: ssd-storage
    emptyDir: {}
""" as String
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml gCloudAndMavenAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "optimize-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
    LABEL = "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: params.IMPORT_TIMEOUT_IN_MINUTES, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      parallel {
        stage('Clone Optimize Repo') {
          steps {
            optimizeCloneGitRepo(params.BRANCH)
          }
        }
        stage('Import ES Snapshot') {
          steps {
            container('maven') {
              // Create repository
              sh ("""
                    echo \$(curl -sq -H "Content-Type: application/json" -d '{ "type": "gcs", "settings": { "bucket": "optimize-data", "base_path": "$params.SNAPSHOT_FOLDER_NAME", "client": "optimize_ci_service_account" }}' -XPUT "http://localhost:9200/_snapshot/my_gcs_repository")
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
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh("""
              mvn -pl backend -am -DskipTests -Dskip.fe.build -Dskip.docker -s \$MAVEN_SETTINGS_XML clean install -B
            """)
            sh("""
              mvn -B -s \$MAVEN_SETTINGS_XML -f qa/import-performance-tests -P zeebe-data-import-performance-test test \
              -Dzeebe.enabled=true \
              -Dzeebe.name=zeebe-record \
              -Dzeebe.partition.count=$params.ZEEBE_PARTITION_COUNT \
              -Dzeebe.maximportpagesize=$params.ZEEBE_MAX_IMPORT_PAGE_SIZE \
              -Ddata.process.definition.count=$params.DATA_PROCESS_DEFINITION_COUNT \
              -Ddata.instance.count=$params.DATA_INSTANCE_COUNT \
              -Dimport.timeout.in.minutes=$params.IMPORT_TIMEOUT_IN_MINUTES \
            """)
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
      container('gcloud'){
        sh 'apt-get install kubectl'
        sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch.log'
        archiveArtifacts artifacts: 'elasticsearch.log', onlyIfSuccessful: false
      }
      retriggerBuildIfDisconnected()
    }
  }
}
