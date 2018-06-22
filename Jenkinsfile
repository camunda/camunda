// vim: set filetype=groovy:

def jdkVersion = 'jdk-8-latest'
def mavenVersion = 'maven-3.5-latest'
def mavenSettingsConfig = 'camunda-maven-settings'

def joinJmhResults = '''\
#!/bin/bash -x
cat **/*/jmh-result.json | jq -s add > target/jmh-result.json
'''

pipeline {
    agent { node { label 'ubuntu-large' } }

    options {
        buildDiscarder(logRotator(daysToKeepStr:'14', numToKeepStr:'50'))
            timestamps()
            timeout(time: 45, unit: 'MINUTES')
    }

    stages {
        stage('Install') {
            steps {
                withMaven(jdk: jdkVersion, maven: mavenVersion, mavenSettingsConfig: mavenSettingsConfig) {
                    sh 'mvn -B clean com.mycila:license-maven-plugin:check com.coveo:fmt-maven-plugin:check install -DskipTests'
                }
            }
        }

        stage('Tests') {
            steps {
                withMaven(jdk: jdkVersion, maven: mavenVersion, mavenSettingsConfig: mavenSettingsConfig) {
                    sh 'mvn -B verify -P skip-unstable-ci'
                }
            }
            post {
                always {
                    junit testResults: '**/target/*-reports/**/*.xml', allowEmptyResults: true
                }
                failure {
                    archiveArtifacts artifacts: '**/target/*-reports/**/*-output.txt,**/**/*.dumpstream', allowEmptyArchive: true
                }
            }
        }

        stage('Deploy') {
            when { branch 'develop' }
            steps {
                withMaven(jdk: jdkVersion, maven: mavenVersion, mavenSettingsConfig: mavenSettingsConfig) {
                    sh 'mvn -B generate-sources source:jar javadoc:jar deploy -DskipTests'
                }
            }
        }

        stage('JMH') {
            steps {
                withMaven(jdk: jdkVersion, maven: mavenVersion, mavenSettingsConfig: mavenSettingsConfig) {
                    sh 'mvn -B integration-test -DskipTests -P jmh'
                }
            }

            post {
                success {
                    sh joinJmhResults
                    jmhReport 'target/jmh-result.json'
                }
            }
        }
    }

    post {
        changed {
            sendBuildStatusNotificationToDevelopers(currentBuild.result)
        }
    }
}

void sendBuildStatusNotificationToDevelopers(String buildStatus = 'SUCCESS') {
    def buildResult = buildStatus ?: 'SUCCESS'
    def subject = "${buildResult}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def details = "${buildResult}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' see console output at ${env.BUILD_URL}'"

    emailext (
        subject: subject,
        body: details,
        recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
    )
}
