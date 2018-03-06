#!/usr/bin/env groovy

def backendModuleName = "backend"

def copySnapshots() {
  script {
    sh 'sh ./.ci/scripts/copy_snapshots.sh'
  }
}

def startElasticsearch() {
  stopAllOptimizeComponents()
  script {
    sh 'sh ./.ci/scripts/start-es.sh'
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

  triggers {
    upstream('camunda-optimize/master', 'SUCCESS')
  }
  
  options {
    // General Jenkins job properties
    buildDiscarder(logRotator(numToKeepStr:'10'))
    // "wrapper" steps that should wrap the entire build execution
    timestamps()
    timeout(time: 10, unit: 'MINUTES')
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
    stage('IT') {
      steps {
        stopAllOptimizeComponents()
        sh 'mvn -s settings.xml -Pit,engine-7.7,jenkins clean'
        startElasticsearch()
        sh 'mvn -s settings.xml -Pproduction,it,engine-7.7,jenkins install'
      }
      post {
        always {
          stopAllOptimizeComponents()
          copySnapshots()
          junit testResults: '**/failsafe-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
          archiveArtifacts artifacts:  backendModuleName + '/target/it-elasticsearch/**/logs/*.log', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/it-elasticsearch/**/_snapshots/*.gz', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/failsafe-reports/*.txt', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/camunda-tomcat/server/apache-tomcat-8.0.24/logs/*.out', onlyIfSuccessful: false
        }
      }

    }
  }

}
