#!/usr/bin/env groovy

def backendModuleName = "backend"

pipeline {
  agent { label 'optimize-build' }

  // Environment
  environment {
    DISPLAY = ":0"
    NODE_ENV = "ci"
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
        sh 'mvn -s settings.xml -Pit,engine-7.7,jenkins  -f ' + backendModuleName + '/pom.xml verify'
      }
      post {
        always {
          stopAllOptimizeComponents()
          copySnapshots()
          junit testResults: '**/failsafe-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
          archiveArtifacts artifacts:  backendModuleName + '/target/it-elasticsearch/**/logs/*.log', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/it-elasticsearch/**/_snapshots/*.gz', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/failsafe-reports/*.txt', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/camunda-tomcat/server/apache-tomcat-8.0.47/logs/*.out', onlyIfSuccessful: false
        }
      }

    }
  }

}
