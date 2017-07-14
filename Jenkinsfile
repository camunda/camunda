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

def copySnapshots() {
  script {
    sh 'sh ./.ci/scripts/copy_snapshots.sh'
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
        stopAllOptimizeComponents()
        sh 'mvn -s settings.xml -Pit,jenkins clean'
        startElasticsearch()
        sh 'mvn -s settings.xml -Pit,jenkins  -f ' + backendModuleName + '/pom.xml verify'
        stopAllOptimizeComponents()
        copySnapshots()
      }
      post {
        always {
          junit testResults: '**/failsafe-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
          archiveArtifacts artifacts:  backendModuleName + '/target/it-elasticsearch/**/logs/*.log', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/failsafe-reports/*.txt', onlyIfSuccessful: false
          archiveArtifacts artifacts:  backendModuleName + '/target/camunda-tomcat/server/apache-tomcat-8.0.24/logs/*.*', onlyIfSuccessful: false
        }
      }

    }
    stage('E2E') {
      steps {
       sh 'sh ./start-e2e.sh'
      }
      post {
        always {
          junit testResults: '**/surefire-reports/**/*.xml', allowEmptyResults: true, healthScaleFactor: 1.0, keepLongStdio: true
        }
        failure {
          archiveArtifacts artifacts: '**/errorShots/*', onlyIfSuccessful: false
          archiveArtifacts artifacts: '**/distro/target/distro/log/*', onlyIfSuccessful: false
        }
      }
    }
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
