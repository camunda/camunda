#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def startElasticsearch() {
  stopAllOptimizeComponents()
  script {
    sh 'sh ./.ci/scripts/start-es.sh'
  }
}

def startEngine() {
  script {
    sh 'sh ./.ci/scripts/start-engine.sh'
  }
}

def stopAllOptimizeComponents() {
  script {
    sh 'sh ./.ci/scripts/kill-all-components.sh'
  }
}


pipeline {

  agent { label 'optimize-build' }
  // Environment
  environment {
    DISPLAY = ":0"
    NODE_ENV = "ci"
  }

  options {
    // General Jenkins job properties
    buildDiscarder(logRotator(numToKeepStr: '10'))
    // "wrapper" steps that should wrap the entire build execution
    timestamps()
    timeout(time: 120, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize', branch: 'master', credentialsId: 'camunda-jenkins-github-ssh', poll: false

        configFileProvider([
            configFile(fileId: 'camunda-maven-settings', replaceTokens: true, targetLocation: 'settings.xml')
        ]) {}
      }
    }
    stage('Performance') {
      steps {
        sh 'mvn -DskipTests -s settings.xml clean install'
        startElasticsearch()
        startEngine()
        sh 'mvn -Ptest-only -f qa/import-performance-tests/pom.xml -s settings.xml clean install'

        stopAllOptimizeComponents()
      }
    }
  }

  post {
    changed {
      emailext subject: "[Jenkins-Optimize] - Status[${currentBuild.rawBuild.result}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}",
          body: """\
${env.JOB_NAME} - Build # ${env.BUILD_NUMBER}:
Status: ${currentBuild.rawBuild.result}
Check console output at ${env.BUILD_URL} to view the results.
""",
          recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
    }
  }
}
