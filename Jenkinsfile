// vim: set filetype=groovy:

@Library(["camunda-ci", "zeebe-jenkins-shared-library"]) _

def buildName = "${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"

//for develop branch keep builds for 7 days to be able to analyse build errors, for all other branches, keep the last 10 builds
def daysToKeep = (env.BRANCH_NAME=='develop') ? '7' : '-1'
def numToKeep = (env.BRANCH_NAME=='develop') ? '-1' : '10'

pipeline {
    agent {
      kubernetes {
        cloud 'zeebe-ci'
        label "zeebe-ci-build_${buildName}"
        defaultContainer 'jnlp'
        yamlFile '.ci/podSpecs/distribution.yml'
      }
    }

    environment {
      NEXUS = credentials("camunda-nexus")
      SONARCLOUD_TOKEN = credentials('zeebe-sonarcloud-token')
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: daysToKeep, numToKeepStr: numToKeep))
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
    }

    stages {
        stage('Prepare') {
            steps {
                script {
                    commit_summary = sh([returnStdout: true, script: 'git show -s --format=%s']).trim()
                    displayNameFull = "#" + BUILD_NUMBER + ': ' + commit_summary

                    if (displayNameFull.length() <= 45) {
                      currentBuild.displayName = displayNameFull
                    } else {
                      displayStringHardTruncate = displayNameFull.take(45)
                      currentBuild.displayName = displayStringHardTruncate.take(displayStringHardTruncate.lastIndexOf(" "))
                    }
                }
                container('maven') {
                    sh '.ci/scripts/distribution/prepare.sh'
                }
                container('maven-jdk8') {
                    sh '.ci/scripts/distribution/prepare.sh'
                }
                container('golang') {
                    sh '.ci/scripts/distribution/prepare-go.sh'
                }

            }
        }

        stage('Build (Java)') {
            environment {
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
            }
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/distribution/build-java.sh'
                    }
                }
                container('maven') {
                    sh 'cp dist/target/zeebe-distribution-*.tar.gz zeebe-distribution.tar.gz'
                }
                stash name: "zeebe-build", includes: "m2-repository/io/zeebe/*/${VERSION}/*"
                stash name: "zeebe-distro", includes: "zeebe-distribution.tar.gz"
            }
        }

        stage('Prepare Tests') {
            environment {
                IMAGE = "camunda/zeebe"
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                TAG = 'current-test'
            }

            steps {
                container('docker') {
                    sh '.ci/scripts/docker/build.sh'
                }
            }
        }



        stage('Test') {
            parallel {
                stage('Go') {
                    steps {
                        container('golang') {
                            sh '.ci/scripts/distribution/build-go.sh'
                        }

                        container('golang') {
                            sh '.ci/scripts/distribution/test-go.sh'
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST-go.xml", keepLongStdio: true
                        }
                    }
                }

                stage('Analyse (Java)') {
                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/analyse-java.sh'
                            }
                        }
                    }
                }

                stage('Unit (Java)') {
                    environment {
                      SUREFIRE_REPORT_NAME_SUFFIX = 'java'
                    }

                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-java.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}.xml", keepLongStdio: true
                        }
                    }
                }
                stage('Unit 8 (Java 8)') {
                    environment {
                      SUREFIRE_REPORT_NAME_SUFFIX = 'java8'
                    }

                    steps {
                        container('maven-jdk8') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-java8.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}.xml", keepLongStdio: true
                        }
                    }
                }

                stage('IT (Java)') {
                    agent {
                        kubernetes {
                            cloud 'zeebe-ci'
                            label "zeebe-ci-build_${buildName}_it"
                            defaultContainer 'jnlp'
                            yamlFile '.ci/podSpecs/distribution.yml'
                        }
                    }

                    environment {
                        SUREFIRE_REPORT_NAME_SUFFIX = 'it'
                        NEXUS = credentials("camunda-nexus")
                        IMAGE = "camunda/zeebe"
                        VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                        TAG = 'current-test'
                    }

                    steps {
                        container('maven') {
                            sh '.ci/scripts/distribution/prepare.sh'
                        }
                        unstash name: "zeebe-build"
                        unstash name: "zeebe-distro"
                        container('docker') {
                            sh '.ci/scripts/docker/build.sh'
                        }
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/it-java.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}.xml", keepLongStdio: true
                        }
                    }
                }

                stage('Build Docs') {
                    steps {
                      retry(3) {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/docs/prepare.sh'
                                sh '.ci/scripts/docs/build.sh'
                            }
                        }
                      }
                    }
                }
            }

            post {
                failure {
                    zip zipFile: 'test-reports.zip', archive: true, glob: "**/*/surefire-reports/**"
                    archive "**/hs_err_*.log"

                    script {
                      if (fileExists('./target/FlakyTests.txt')) {
                          currentBuild.description = "Flaky Tests: <br>" + readFile('./target/FlakyTests.txt').split('\n').join('<br>')
                      }
                    }
                }
            }
        }

        stage('Upload') {
            when { branch 'develop' }
            steps {
                retry(3) {
                    container('maven') {
                        configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                            sh '.ci/scripts/distribution/upload.sh'
                        }
                    }
                }
            }
        }

        stage('Post') {
            parallel {
                stage('Docker') {
                    when { branch 'develop' }

                    environment {
                        VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                    }

                    steps {
                        retry(3) {
                            build job: 'zeebe-docker', parameters: [
                                string(name: 'BRANCH', value: env.BRANCH_NAME),
                                string(name: 'VERSION', value: env.VERSION),
                                booleanParam(name: 'IS_LATEST', value: env.BRANCH_NAME == 'master'),
                                booleanParam(name: 'PUSH', value: env.BRANCH_NAME == 'develop')
                            ]
                        }
                    }
                }

                stage('Docs') {
                    when { anyOf { branch 'master'; branch 'develop' } }
                    steps {
                        retry(3) {
                            build job: 'zeebe-docs', parameters: [
                                string(name: 'BRANCH', value: env.BRANCH_NAME),
                                booleanParam(name: 'LIVE', value: env.BRANCH_NAME == 'master')
                            ]
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            // Retrigger the build if there were connection issues
            script {
                if (agentDisconnected()) {
                    currentBuild.result = 'ABORTED'
                    currentBuild.description = "Aborted due to connection error"

                    build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
                }
            }
        }
        changed {
            script {
                if (env.BRANCH_NAME == 'develop') {
                    slackSend(
                        channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
                        message: "Zeebe ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
                }
            }
        }
    }
}
