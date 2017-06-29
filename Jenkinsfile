#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

def commitId() {
  if (!env.COMMIT_ID) {
    script {
      sh 'git rev-parse --short=7 --verify HEAD | tr -d "\n" > GIT_COMMIT'
      return env.COMMIT_ID = readFile('GIT_COMMIT')
    }
  }
}

def startElasticsearch() {
  script {
    sh '''
      mkdir -p ./backend/target/it-elasticsearch
      cd ./backend/target/it-elasticsearch
      wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.4.3.tar.gz
      tar -xzvf ./elasticsearch-5.4.3.tar.gz
      ./elasticsearch-5.4.3/bin/elasticsearch -d
    '''
  }
}

def stopElasticsearch() {
  script {
    sh '''
      kill -9  $(ps -ef | grep elasticsearch | awk \'{ print $2 }\')
    '''
  }
}

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
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        configFileProvider([
          configFile(fileId: 'camunda-maven-settings', replaceTokens: true, targetLocation: 'settings.xml')
        ]) {}
      }
    }
    stage('Linting') {
      steps {
        sh '''
          cd ./client
          yarn
          yarn run eslint
        '''
      }
    }
    stage('Unit') {
      steps {
        sh 'mvn -Pproduction -s settings.xml clean install'
      }
      post {
        always {
          junit testResults: '**/surefire-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
        }
      }
    }
    stage('JS-Unit') {
      steps {
        sh '''
          cd ./client
          yarn
          yarn run test
        '''
      }
      post {
        always {
          junit testResults: '**/surefire-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
        }
      }
    }
    stage('IT') {
      steps {
        startElasticsearch()
        sh 'mvn -s settings.xml -Pit,jenkins  -f ' + backendModuleName + '/pom.xml clean verify'
      }
      post {
        always {
          junit testResults: '**/failsafe-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
          archiveArtifacts artifacts:  backendModuleName + '/target/it-elasticsearch/logs/*.log', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/failsafe-reports/*.txt', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/camunda-tomcat/server/apache-tomcat-8.0.24/logs/*.*', onlyIfSuccessful: false
        }
        stopElasticsearch()
      }

    }
     //stage('E2E') {
      // steps {
      //   sh 'sh ./start-e2e.sh'
      // }
      // post {
      //   always {
      //     junit testResults: '**/surefire-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
      //   }
      //   failure {
      //     archiveArtifacts artifacts: '**/errorShots/*', onlyIfSuccessful: false
      //   }
      // }
     // }
    stage('Docs') {
      steps {
        sh 'mvn -s settings.xml -f ' + backendModuleName + '/pom.xml -DskipTests -Pdocs clean package'
      }
      post {
        success {
          archiveArtifacts artifacts: backendModuleName + '/target/docs/**/*.*', onlyIfSuccessful: true
        }
      }
    }
    stage('Deploy to Nexus') {
      when {
        branch 'master'
      }
      steps {
        sh 'mvn -Pproduction -s settings.xml -DskipTests clean deploy'
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
