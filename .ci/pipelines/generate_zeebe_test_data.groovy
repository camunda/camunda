#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }

def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

String agent() {
  """
metadata:
  labels:
    agent: operate-ci-build
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
      - "sysctl -w vm.max_map_count=262144 && \\
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
      - "elasticsearch-plugin install --batch repository-gcs && \\
        elasticsearch-keystore create && \\
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
          cpu: 8
          memory: 8Gi
        requests:
          cpu: 8
          memory: 8Gi
    - name: elasticsearch
      image: docker.elastic.co/elasticsearch/elasticsearch:${params.ES_VERSION}
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
      image: camunda/zeebe:${params.ZEEBE_VERSION}
      env:
        - name: ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME
          value: io.camunda.zeebe.exporter.ElasticsearchExporter
        - name: ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT
          value: ${params.ZEEBE_PARTITION_COUNT}
      resources:
        limits:
          cpu: 10
          memory: 16Gi
        requests:
          cpu: 10
          memory: 16Gi
  volumes:
  - name: configdir
    emptyDir: {}
  - name: plugindir
    emptyDir: {}
  - name: ci-service-account
    secret: 
      secretName: ci-service-account
""" as String
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml agent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "optimize-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
    LABEL = "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
  }

  options {
    buildDiscarder(logRotator(daysToKeepStr: '10'))
    timestamps()
    timeout(time: 7, unit: 'DAYS')
  }

  stages {
    stage('Prepare') {
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        container('maven') {
          // Compile Optimize and skip tests
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh('mvn -B -s $MAVEN_SETTINGS_XML -DskipTests -Dskip.fe.build -Dskip.docker clean install -B')
          }
        }
      }
    }
    stage('Data Generation') {
      steps {
        container('maven') {
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            // Generate Data
            sh("""
              mvn -B -s $MAVEN_SETTINGS_XML -f qa/zeebe-data-generation spring-boot:run \
              -Dinstance.starter.thread.count=$params.INSTANCE_STARTER_THREAD_COUNT \
              -Djobworker.execution.thread.count=$params.JOBWORKER_EXECUTION_THREAD_COUNT \
              -Djobworker.max.active.count=$params.JOBWORKER_MAX_ACTIVE_COUNT \
              -Ddata.process.definition.count=$params.NUM_PROCESS_DEFINITIONS \
              -Ddata.instance.count=$params.NUM_PROCESS_INSTANCES \
            """)
          }
        }
      }
    }
    stage('Upload snapshot') {
      steps {
        container('maven') {
          // Create repository
          sh("""
                echo \$(curl -qs -H "Content-Type: application/json" -d '{ "type": "gcs", "settings": { "bucket": "optimize-data", "base_path": "$params.SNAPSHOT_FOLDER_NAME", "client": "optimize_ci_service_account" }}' -XPUT "http://localhost:9200/_snapshot/my_gcs_repository")
            """)
          // Delete previous Snapshot
          sh("""
                echo \$(curl -qs -XDELETE "http://localhost:9200/_snapshot/my_gcs_repository/snapshot_1")
            """)
          // Trigger Snapshot
          sh("""
                echo \$(curl -qs -H "Content-Type: application/json" -XPUT "http://localhost:9200/_snapshot/my_gcs_repository/snapshot_1?wait_for_completion=true" -d '{"indices": "zeebe-record*", "ignore_unavailable": "true", "include_global_state": false}')
            """)
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
      }
      archiveArtifacts artifacts: 'elasticsearch.log', onlyIfSuccessful: false
      retriggerBuildIfDisconnected()
    }
  }
}
