#!/usr/bin/env groovy

String agent() {
  boolean isStage = env.JENKINS_URL.contains('stage')
  String vaultPrefix = isStage ? 'stage.' : ''
  String prefix = isStage ? 'stage-' : ''
  """
---
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: operate-ci-build
    camunda.cloud/source: jenkins
    camunda.cloud/managed-by: "${env.JENKINS_DOMAIN}"
  annotations:
    camunda.cloud/created-by: "${env.BUILD_URL}"
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-preempt
  serviceAccountName: ${prefix}ci-operate-camunda-cloud
  tolerations:
    - key: "agents-n1-standard-32-netssd-preempt"
      operator: "Exists"
      effect: "NoSchedule"
  initContainers:
    - name: vault-template
      image: gcr.io/camunda-public/camunda-internal_vault-template
      imagePullPolicy: Always
      env:
      - name: VAULT_ADDR
        value: https://${vaultPrefix}vault.int.camunda.com/
      - name: CLUSTER
        value: camunda-ci
      - name: SA_NAMESPACE
        valueFrom:
          fieldRef:
            apiVersion: v1
            fieldPath: metadata.namespace
      - name: SA_NAME
        valueFrom:
          fieldRef:
            apiVersion: v1
            fieldPath: spec.serviceAccountName
      volumeMounts:
      - mountPath: /etc/consul-templates
        name: vault-config
      - mountPath: /etc/vault-output
        name: vault-output
    - name: init-sysctl
      image: docker.elastic.co/elasticsearch/elasticsearch:7.16.2
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
      image: docker.elastic.co/elasticsearch/elasticsearch:7.16.2
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
        name: vault-output
        readOnly: true
  containers:
    - name: node
      image: node:16-alpine
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
          memory: 2Gi
        requests:
          cpu: 2
          memory: 2Gi
    - name: elasticsearch
      image: docker.elastic.co/elasticsearch/elasticsearch:7.16.2
      env:
        - name: ES_JAVA_OPTS
          value: '-Xms512m -Xmx512m'
        - name: cluster.name
          value: docker-cluster
        - name: discovery.type
          value: single-node
        - name: xpack.security.enabled
          value: "false"
        - name: action.auto_create_index
          value: "true"
        - name: bootstrap.memory_lock
          value: "true"
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
          cpu: 2
          memory: 4Gi
        requests:
          cpu: 2
          memory: 4Gi
    - name: zeebe
      image: camunda/zeebe:SNAPSHOT
      imagePullPolicy: Always   #this must be uncommented when snapshot is used
      env:
        - name: ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME
          value: io.camunda.zeebe.exporter.ElasticsearchExporter
        - name: ZEEBE_BROKER_BACKPRESSURE_ENABLED
          value: false
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
    - name: operate
      image: camunda/operate:SNAPSHOT
      imagePullPolicy: Always   #this must be uncommented when snapshot is used
      env:
        - name: CAMUNDA_OPERATE_ARCHIVER_WAIT_PERIOD_BEFORE_ARCHIVING
          value: 1m
      resources:
        limits:
          cpu: 1
          memory: 2Gi
        requests:
          cpu: 1
          memory: 2Gi
  volumes:
  - name: configdir
    emptyDir: {}
  - name: plugindir
    emptyDir: {}
  - name: vault-output
    emptyDir:
      medium: Memory
  - name: vault-config
    configMap:
      name: ${prefix}ci-operate-vault-templates
""" as String
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'operate-ci'
      label "operate-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml agent()
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        container('node'){
          git url: 'https://github.com/camunda-cloud/operate.git',
            branch: "${params.OPERATE_BRANCH}",
            credentialsId: 'github-cloud-operate-app',
            poll: false
          dir('client') {
            sh '''
            yarn install
            '''
          }
        }
      }
    }

    stage('E2E tests') {
      environment {
        BROWSERSTACK_USERNAME = credentials('browserstack-username')
        BROWSERSTACK_ACCESS_KEY = credentials('browserstack-accesskey')
        OPERATE_BROWSERSTACK_BROWSER = "${params.OPERATE_BROWSERSTACK_BROWSER}"
      }
      steps {
        container('node') {
          dir('client') {
            sh 'yarn run test:ci-browserstack'
          }
        }
      }
    }
  }
}
