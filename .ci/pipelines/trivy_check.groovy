#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }

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
  volumes:
    - name: dshm
      emptyDir:
      medium: Memory
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    volumeMounts:
      - mountPath: /dev/shm
        name: dshm
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 4
        memory: 8Gi
      requests:
        cpu: 4
        memory: 8Gi
  - name: dind
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
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
  - name: docker
    image: crazymax/docker:20.10.16
    command: ["/bin/bash"]
    env:
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 2Gi
      requests:
        cpu: 1
        memory: 2Gi
"""
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      inheritFrom "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenAgent()
    }
  }
  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()
      }
    }
    stage('Build') {
      steps {
        container('maven') {
          runMaven("-B package --file pom.xml -Dmaven.test.skip")
        }
      }
    }
    stage('Trivy test') {
      failFast false
      steps {
        container('docker') {
          sh("""#!/bin/bash -eux
          docker buildx build --load --build-arg VERSION=3.10.0-SNAPSHOT -t optimize/test:latest .
          docker run -v /var/run/docker.sock:/var/run/docker.sock -v $HOME/Library/Caches:/root/.cache/ aquasec/trivy:latest image --no-progress --ignore-unfixed --severity HIGH,CRITICAL --exit-code 1 optimize/test:latest
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
      retriggerBuildIfDisconnected()
    }
  }
}
