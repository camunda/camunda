#!/usr/bin/env groovy

// TODO: Use parameters for different migrations 
// Defaults:
//  elasticsearch-6.8.1
//  zeebe-0.19.0
//  operate-1.0.0 
//  maven-3.6.1 - Used for migration, test and validation
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
      image: camunda/zeebe:0.21.1
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
    - name: operate
      image: camunda/operate:1.0.0
      env:
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
            // Compile QA
            sh('mvn -B -s $MAVEN_SETTINGS_XML -f qa -DskipTests clean install')
            // Generate Data
            sh('mvn -B -s $MAVEN_SETTINGS_XML -f qa/migration spring-boot:run -Dmigration.app=DataGenerationApp')
            sh('sleep 10')
          }
        }
	  }
	}
	stage('Migrate data') {
		steps {
		   container('maven') {
		   	 // migrate from 1.0.0 to 1.1.0
			 sh("cd ./migration/v1.0.0-to-1.1.0 && sh ./migrate.sh")
			 // wait a few seconds
			 sh("sleep 5")
			 // migrate from 1.1.0 to 1.2.0
			 sh("cd ./migration/v1.1.0-to-1.2.0 && sh ./migrate.sh")
		 }
	  }
	}
	stage('Check migration results') {
		steps {
		  container('maven') {
		      configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
		        // Validate operate indices 
		        sh('mvn -B -s $MAVEN_SETTINGS_XML -f qa/migration spring-boot:run -Dmigration.app=ValidationApp')
		      }
          }
       }
    }
  }

  post {
    failure {
	  script {
		def notification = load "build_notification.groovy"
        notification.buildNotification(currentBuild.result)
      }
    }
  }
}
