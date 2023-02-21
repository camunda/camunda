#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { return "agents-n1-standard-8-netssd-preempt" }

static String mavenElasticsearchAgent() {
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
    image: maven:3.8.6-openjdk-11-slim
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
        cpu: 3
        memory: 3Gi
      requests:
        cpu: 3
        memory: 3Gi
"""
}

void buildNotification() {
  String BACKEND_DIFF = sh(script: 'cd ./util/dependency-doc-creation/ && cat backend_diff.md', returnStdout: true)
          .replaceAll(">", "Added dependency:")
          .replaceAll("<", "Removed dependency:")
          .trim()

  String FRONTEND_DIFF = sh(script: 'cd ./util/dependency-doc-creation/ && cat frontend_diff.md', returnStdout: true)
          .replaceAll(">", "Added dependency:")
          .replaceAll("<", "Removed dependency:")
          .trim()

  FRONTEND_DIFF = buildDiff(FRONTEND_DIFF, "FRONTEND")
  BACKEND_DIFF = buildDiff(BACKEND_DIFF, "BACKEND")

  if (FRONTEND_DIFF || BACKEND_DIFF) {
    sendEmail(FRONTEND_DIFF, BACKEND_DIFF)
  }
}

private static String buildDiff(String DIFF, String end) {
  DIFF ? "${end} DEPENDENCIES CHANGED:<br>" +
          "${DIFF}<br>" +
          "----------------------------------------<br><br>" :
          ""
}

private void sendEmail(String FRONTEND_DIFF, String BACKEND_DIFF) {
  String subject = "Optimize dependencies changed - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"

  String body =
          "Hello Optimize team, <br><br>" +
          "while checking the dependencies of the Camunda Optimize repository, the following changes were detected:<br><br>" +
          "${FRONTEND_DIFF}" +
          "${BACKEND_DIFF}" +
          "Please check if there are any unusual licenses for the new dependencies. If you are not sure if you are" +
          " allowed to use libraries with the given dependencies please notify/ask the Legal team.<br><br>" +
          "Best<br>" +
          "Your Optimize Dependency Check Bot"

  emailext subject: subject, body: body, to: "optimize@camunda.com"
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenElasticsearchAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        optimizeCloneGitRepo(params.BRANCH)
      }
    }
    stage('Create dependency lists') {
      steps {
        container('maven') {
          configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
            sh("""
              apt-get update && \
              apt-get -y install git openssh-client

              git config --global --add safe.directory "\$PWD"

              cd ./util/dependency-doc-creation/
              ./createOptimizeDependencyFiles.sh useCISettings
  
              mv backend-dependencies.md ./curr_backend-dependencies.md
              mv frontend-dependencies.md ./curr_frontend-dependencies.md
  
              mkdir -p ~/.ssh
              ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
  
              git config --global user.email "ci_automation@camunda.com"
              git config --global user.name "${optimizeUtils.defaultCredentialsId()}"
  
              git checkout `git log -1 --before=\\"\$(date -d "yesterday" +%d.%m.%Y)\\" --pretty=format:"%h"`
  
              ./createOptimizeDependencyFiles.sh useCISettings
  
              # diff returns status code 1 if there is difference, so the script crashes
              # || : executes a null operator if diff fails, so the script continues running
              diff backend-dependencies.md curr_backend-dependencies.md > backend_diff.md || :
              diff frontend-dependencies.md curr_frontend-dependencies.md > frontend_diff.md || :
  
              # cut first line of the diff files (first line describes the changes)
              sed '1d' frontend_diff.md > tmpfile; mv tmpfile frontend_diff.md
              sed '1d' backend_diff.md > tmpfile; mv tmpfile backend_diff.md
          """)
            buildNotification()
          }
        }
      }
    }
  }
  post {
    always {
      retriggerBuildIfDisconnected()
    }
  }
}
