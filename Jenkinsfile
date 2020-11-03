// vim: set filetype=groovy:

@Library(["camunda-ci", "zeebe-jenkins-shared-library"]) _

def buildName = "${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"

def masterBranchName = 'master'
def isMasterBranch = env.BRANCH_NAME == masterBranchName
def developBranchName = 'develop'
def isDevelopBranch = env.BRANCH_NAME == developBranchName

//for develop branch keep builds for 7 days to be able to analyse build errors, for all other branches, keep the last 10 builds
def daysToKeep = isDevelopBranch ? '7' : '-1'
def numToKeep = isDevelopBranch ? '-1' : '10'

//the develop branch should be run hourly to detect flaky tests and instability, other branches only on commit
def cronTrigger = isDevelopBranch ? '@hourly' : ''

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

    triggers {
        cron(cronTrigger)
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: daysToKeep, numToKeepStr: numToKeep))
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        booleanParam(name: 'RUN_QA', defaultValue: false, description: "Run QA Stage")
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
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/distribution/build-java.sh'
                    }
                }
            }
        }

        stage('Prepare Tests') {
            environment {
                IMAGE = "camunda/zeebe"
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                TAG = 'current-test'
            }

            steps {
                container('maven') {
                    sh 'cp dist/target/zeebe-distribution-*.tar.gz zeebe-distribution.tar.gz'
                }

                container('docker') {
                    sh '.ci/scripts/docker/build.sh'
                    sh '.ci/scripts/docker/build_zeebe-hazelcast-exporter.sh'
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
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/analyse-java.sh'
                            }
                        }
                    }
                }

                stage('Unit (Java)') {
                    environment {
                        SUREFIRE_REPORT_NAME_SUFFIX = 'java-testrun'
                    }

                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-java.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}*.xml", keepLongStdio: true
                        }
                    }
                }
                stage('Unit 8 (Java 8)') {
                    environment {
                        SUREFIRE_REPORT_NAME_SUFFIX = 'java8-testrun'
                    }

                    steps {
                        container('maven-jdk8') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-java8.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}*.xml", keepLongStdio: true
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
                        SUREFIRE_REPORT_NAME_SUFFIX = 'it-testrun'
                        NEXUS = credentials("camunda-nexus")
                        IMAGE = "camunda/zeebe"
                        VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                        TAG = 'current-test'
                    }

                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/prepare.sh'
                                sh '.ci/scripts/distribution/build-java.sh'
                                sh 'cp dist/target/zeebe-distribution-*.tar.gz zeebe-distribution.tar.gz'
                            }
                        }
                        container('docker') {
                            sh '.ci/scripts/docker/build.sh'
                        }
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/it-java.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}*.xml", keepLongStdio: true
                        }
                    }
                }

                stage('BPMN TCK') {
                    steps {
                        container('maven') {
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh '.ci/scripts/distribution/test-tck.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "bpmn-tck/**/*/TEST*.xml", keepLongStdio: true
                        }
                    }
                }
            }

            post {
                always {
                    jacoco(
                            execPattern: '**/*.exec',
                            classPattern: '**/target/classes',
                            sourcePattern: '**/src/main/java,**/generated-sources/protobuf/java,**/generated-sources/assertj-assertions,**/generated-sources/sbe',
                            exclusionPattern: '**/io/zeebe/gateway/protocol/**,'
                                    + '**/*Encoder.class,**/*Decoder.class,**/MetaAttribute.class,'
                                    + '**/io/zeebe/protocol/record/**/*Assert.class,**/io/zeebe/protocol/record/Assertions.class,', // classes from generated resources
                            runAlways: true
                    )
                    zip zipFile: 'test-coverage-reports.zip', archive: true, glob: "**/target/site/jacoco/**"
                }
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

        stage('QA') {
            //when {
            //    expression { params.RUN_QA }
            //}
            environment {
                IMAGE = "gcr.io/zeebe-io/zeebe"
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                TAG = "${env.GIT_COMMIT}"
                DOCKER_GCR = credentials("zeebe-gcr-serviceaccount-json")
            }

            steps {
                container('docker') {
                    sh 'cp dist/target/zeebe-distribution-*.tar.gz zeebe-distribution.tar.gz'
                    sh '.ci/scripts/docker/build.sh'
                    sh '.ci/scripts/docker/upload-gcr.sh'
                }
            }
        }

        stage('Upload') {
            when { allOf { branch developBranchName; not { triggeredBy 'TimerTrigger' } } }
            steps {
                retry(3) {
                    container('maven') {
                        configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                            sh '.ci/scripts/distribution/upload.sh'
                        }
                    }
                }
            }
        }

        stage('Post') {
            when { not { triggeredBy 'TimerTrigger' } }

            parallel {
                stage('Docker') {
                    when { branch developBranchName }

                    environment {
                        VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                    }

                    steps {
                        retry(3) {
                            build job: 'zeebe-docker', parameters: [
                                    string(name: 'BRANCH', value: env.BRANCH_NAME),
                                    string(name: 'VERSION', value: env.VERSION),
                                    booleanParam(name: 'IS_LATEST', value: isMasterBranch),
                                    booleanParam(name: 'PUSH', value: isDevelopBranch)
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

                String userReason = null
                if (currentBuild.description ==~ /.*Flaky Tests.*/) {
                    userReason = 'flaky-tests'
                }
                org.camunda.helper.CIAnalytics.trackBuildStatus(this, userReason)
            }
        }
        failure {
            script {
                if (env.BRANCH_NAME != 'develop' || agentDisconnected()) {
                    return
                }

                echo "Send slack message"
                slackSend(
                        channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
                        message: "Zeebe ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
            }
        }
        changed {
            script {
                if (env.BRANCH_NAME != 'develop' || agentDisconnected()) {
                    return
                }
                if (currentBuild.currentResult == 'FAILURE') {
                    return // already handled above
                }

                if (hasBuildResultChanged()) {
                    echo "Send slack message"
                    slackSend(
                            channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
                            message: "Zeebe ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
                }
            }
        }
    }
}
