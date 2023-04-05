#!/usr/bin/env groovy

String agent() {
  boolean isStage = env.JENKINS_URL.contains('stage')
  String prefix = isStage ? 'stage-' : ''
  """
---
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: tasklist-ci-build
    camunda.cloud/source: jenkins
    camunda.cloud/managed-by: "${env.JENKINS_DOMAIN}"
  annotations:
    camunda.cloud/created-by: "${env.BUILD_URL}"
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-preempt
  serviceAccountName: ${prefix}ci-zeebe-tasklist-camunda-cloud
  tolerations:
    - key: "agents-n1-standard-32-netssd-preempt"
      operator: "Exists"
      effect: "NoSchedule"
  initContainers:
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
  containers:
    - name: maven
      image: maven:3-openjdk-17
      command: ["cat"]
      tty: true
      env:
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
        - name: DOCKER_HOST
          value: tcp://localhost:2375
      resources:
        limits:
          cpu: 2
          memory: 8Gi
        requests:
          cpu: 2
          memory: 8Gi
    - name: docker
      image: docker:20.10.16-dind
      args: ["--storage-driver=overlay2"]
      securityContext:
        privileged: true
      env:
        # This disabled automatic TLS setup as this is not exposed to the network anyway
        - name: DOCKER_TLS_CERTDIR
          value: ""
      tty: true
      resources:
        limits:
          cpu: 3
          memory: 12Gi
        requests:
          cpu: 3
          memory: 12Gi
  volumes:
  - name: configdir
    emptyDir: {}
  - name: plugindir
    emptyDir: {}
""" as String
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'zeebe-tasklist-ci'
      label "tasklist-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
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
    timeout(time: 1, unit: 'HOURS')
  }

  stages {
    stage('Prepare') {
      steps {
         container('maven'){
         	// checkout current tasklist
            git url: 'https://github.com/camunda/tasklist.git',
                branch: "master",
                credentialsId: 'github-tasklist-app',
                poll: false
            // compile current tasklist
            configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
                sh ('mvn -B -s $MAVEN_SETTINGS_XML -DskipTests -P skipFrontendBuild clean install')
            }
         }
      }
    }
    stage('Run backup and restore test') {
      steps {
        container('maven') {
          configFileProvider([configFile(fileId: 'maven-nexus-settings', variable: 'MAVEN_SETTINGS_XML')]) {
            sh ('mvn -B -s $MAVEN_SETTINGS_XML -f qa/backup-restore-tests -P -docker,-skipTests verify')
          }
        }
      }
      post {
        always {
          junit testResults: 'qa/backup-restore-tests/target/*-reports/**/*.xml', keepLongStdio: true, allowEmptyResults: true
        }
      }
    }
  }
  post {
    failure {
	  script {
        def notification = load "${pwd()}/.ci/pipelines/build_notification.groovy"
        notification.buildNotification(currentBuild.result)

        slackSend(
          channel: "#tasklist-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
          message: "Job build ${currentBuild.absoluteUrl} failed!")
      }
    }
  }
}
