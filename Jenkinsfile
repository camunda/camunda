// vim: set filetype=groovy:


def buildName = "${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"

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
        buildDiscarder(logRotator(daysToKeepStr: '-1', numToKeepStr: '10'))
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
    }

    stages {
        stage('Prepare') {
            steps {
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
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/distribution/build-java.sh'
                    }
                }
            }
        }

        stage('Build (Go)') {
            environment {
                IMAGE = "camunda/zeebe"
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                TAG = 'current-test'
            }

            steps {
                container('golang') {
                    sh '.ci/scripts/distribution/build-go.sh'
                }

                container('maven') {
                    sh 'cp dist/target/zeebe-distribution-*.tar.gz zeebe-distribution.tar.gz'
                }

                container('docker') {
                    sh '.ci/scripts/docker/build.sh'
                }
            }
        }

        stage('Test (Go)') {
            steps {
                container('golang') {
                    sh '.ci/scripts/distribution/test-go.sh'
                }
            }

            post {
                always {
                    junit testResults: "**/*/TEST-*.xml", keepLongStdio: true
                }
            }
 
        }

        stage('Test (Java)') {
            parallel {
                stage('Analyse (Java)') {
                      steps {
                          container('maven') {
                               configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                    sh '.ci/scripts/distribution/analyse-java.sh'
                               }
                          }
                      }
                }

                stage('Unit (Java)') {
                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-java.sh'
                            }
                        }
                    }
                }
                stage('Unit 8 (Java 8)') {
                    steps {
                        container('maven-jdk8') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-java8.sh'
                            }
                        }
                    }
                }

                stage('IT (Java)') {
                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/it-java.sh'
                            }
                        }
                    }
                }

                stage('Build Docs') {
                    steps {
                        container('maven') {
                            sh '.ci/scripts/docs/prepare.sh'
                            sh '.ci/scripts/docs/build.sh'
                        }
                    }
                }
            }

            post {
                always {
                    junit testResults: "**/*/TEST-*.xml", keepLongStdio: true
                }
            }
        }

        stage('Upload') {
            when { branch 'develop' }
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/distribution/upload.sh'
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
                        build job: 'zeebe-docker', parameters: [
                            string(name: 'BRANCH', value: env.BRANCH_NAME),
                            string(name: 'VERSION', value: env.VERSION),
                            booleanParam(name: 'IS_LATEST', value: env.BRANCH_NAME == 'master')
                        ]
                    }
                }

                stage('Docs') {
                    when { anyOf { branch 'master'; branch 'develop' } }
                    steps {
                        build job: 'zeebe-docs', parameters: [
                            string(name: 'BRANCH', value: env.BRANCH_NAME),
                            booleanParam(name: 'LIVE', value: env.BRANCH_NAME == 'master')
                        ]
                    }
                }
            }
        }
    }

    post {
        always {
            // Retrigger the build if there were connection issues
            script {
                if (connectionProblem()) {
                    build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
                }
            }
        }
    }
}

boolean connectionProblem() {
  return currentBuild.rawBuild.getLog(500).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException|Connection reset|ProtocolException).*/
}
