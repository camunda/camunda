#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

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
    timeout(time: 30, unit: 'MINUTES')
  }
  
  triggers {
    upstream upstreamProjects: 'camunda-optimize/master', threshold: hudson.model.Result.SUCCESS
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize', branch: '*/master', credentialsId: 'camunda-jenkins-github-ssh', poll: false

        configFileProvider([
            configFile(fileId: 'camunda-maven-settings', replaceTokens: true, targetLocation: 'settings.xml')
        ]) {}
      }
    }
    stage('Unit') {
      steps {
        sh 'mvn -s settings.xml clean install'
      }
      post {
        always {
          junit testResults: '**/surefire-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
        }
      }
    }
    stage('Performance') {
      steps {
        sh 'mvn -Pservice-perf-tests -f qa/service-performance-tests/pom.xml -s settings.xml clean verify'
        sh 'mvn -f qa/service-performance-tests/pom.xml -s settings.xml site'
      }
      post {
        always {
          archiveArtifacts '**/site/**/*.*'
        }
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
