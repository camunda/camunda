#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

overallTimeoutInMinutes = 60
stageTimeoutInMinutes = 15

def commitId() {
  if (!env.COMMIT_ID) {
    script {
      sh 'git rev-parse --short=7 --verify HEAD | tr -d "\n" > GIT_COMMIT'
      return env.COMMIT_ID = readFile('GIT_COMMIT')
    }
  }
}



pipeline {
  agent { label 'optimize-build' }

  // Environment
  environment {
    DISPLAY = ":0"
  }

  options {
    // General Jenkins job properties
    buildDiscarder(logRotator(numToKeepStr:'10'))
    // "wrapper" steps that should wrap the entire build execution
    timestamps()
    timeout(time: overallTimeoutInMinutes, unit: 'MINUTES')
  }

  stages {
    stage('Unit') {
      steps {
        timeout(time: stageTimeoutInMinutes, unit: 'MINUTES') {
          configFileProvider([
              configFile(fileId: 'camunda-maven-settings', replaceTokens: true, targetLocation: 'settings.xml')
          ]) {
            sh '''\
            cd client
            yarn
            mvn -s ${WORKSPACE}/settings.xml package
            '''
          }
        }
      }
      post {
        always {
          junit testResults: '**/surefire-reports/*.xml', allowEmptyResults: true, healthScaleFactor: 1.5, keepLongStdio: true
        }
      }
    }
    stage('IT') {
      steps {
        timeout(time: stageTimeoutInMinutes, unit: 'MINUTES') {
          configFileProvider([
              configFile(fileId: 'camunda-maven-settings', replaceTokens: true, targetLocation: 'settings.xml')
          ]) {
            sh 'mvn -s settings.xml -f optimize-backend/pom.xml clean verify'
          }
        }
      }
    }
    stage('Docs') {
      steps {
        timeout(time: stageTimeoutInMinutes, unit: 'MINUTES') {
          configFileProvider([
              configFile(fileId: 'camunda-maven-settings', replaceTokens: true, targetLocation: 'settings.xml')
          ]) {
            sh 'mvn -s settings.xml -f optimize-backend/pom.xml -DskipTests -Pdocs clean package'
          }
        }
      }
      post {
        success {
          archiveArtifacts artifacts: 'optimize-backend/target/docs/**/*.*', onlyIfSuccessful: true
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
