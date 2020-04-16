#!/usr/bin/env groovy

// TODO: Use parameters for different migrations 
// Defaults:
//  elasticsearch-6.8.6
//  zeebe-0.21.1
//  operate-1.1.0 
//  maven-3.6.1 - Used for migration, test and validation
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
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.8
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
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.8
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
      image: docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.8
      env:
        - name: ES_JAVA_OPTS
          value: '-Xms512m -Xmx512m'
        - name: cluster.name
          value: docker-cluster
        - name: discovery.type
          value: single-node
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
      image: camunda/zeebe:0.23.0
      env:
        - name: ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME
          value: io.zeebe.exporter.ElasticsearchExporter
      resources:
        limits:
          cpu: 4
          memory: 8Gi
        requests:
          cpu: 4
          memory: 8Gi
    - name: operate
      image: camunda/operate:0.23.0
      env:
        - name: CAMUNDA_OPERATE_CSRF_PREVENTION_ENABLED
          value: false
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

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 45, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
         container('maven'){
         	// checkout current operate
            git url: 'git@github.com:camunda/camunda-operate',
                branch: "master",
                credentialsId: 'camunda-jenkins-github-ssh',
                poll: false
            // compile current operate
            configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                sh ('mvn -B -s $MAVEN_SETTINGS_XML -DskipTests -P skipFrontendBuild clean install')
            }
         }
      }
    }
	stage('Create testdata') {
	  steps {
		 container('maven') {
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            // Compile els-schema
            sh('mvn -B -s $MAVEN_SETTINGS_XML -f els-schema -DskipTests clean install')
            // Compile QA
            sh('mvn -B -s $MAVEN_SETTINGS_XML -f qa -DskipTests clean install')
            // Generate Data
            sh('mvn -B -s $MAVEN_SETTINGS_XML -f qa/migration-tests spring-boot:run')
            sh('sleep 5')
          }
        }
	  }
	}
	stage('Migrate data') {
		steps {
		   container('maven') {
		   	 // Migrate 
			  sh("bash ./els-schema/target/classes/migrate.sh")
		 }
	  }
	}
	stage('Check migration results') {
		steps {
		  container('maven') {
		      configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
		        // Validate operate indices 
		        sh('mvn -B -s $MAVEN_SETTINGS_XML -f qa/migration-tests -DskipTests=false verify')
		      }
          }
		}
        post {
        	always {
          	junit testResults: 'qa/migration-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
        	}
      	}
    }
  }
  post {
    failure {
	  script {
        def notification = load "${pwd()}/.ci/pipelines/build_notification.groovy"
        notification.buildNotification(currentBuild.result)
      }
    }
  }
}
