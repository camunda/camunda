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

def shortTimeoutMinutes = 10
def longTimeoutMinutes = 45

//the develop branch should be run hourly to detect flaky tests and instability, other branches only on commit
def cronTrigger = isDevelopBranch ? '@hourly' : ''

pipeline {
    agent {
        kubernetes {
            cloud 'zeebe-ci'
            label "zeebe-ci-build_${buildName}"
            defaultContainer 'jnlp'
            yamlFile ".ci/podSpecs/distribution.yml"
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
    }

    parameters {
        booleanParam(name: 'RUN_QA', defaultValue: false, description: "Run QA Stage")
        string(name: 'GENERATION_TEMPLATE', defaultValue: 'Zeebe 0.x.0', description: "Generation template for QA tests (the QA test will be run with this Zeebe version and Operate/Elasticsearch version from the generation template)")
    }

    stages {
        stage('Prepare Distribution') {
            steps {
                timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                    setHumanReadableBuildDisplayName()

                    prepareMavenContainer()
                    prepareMavenContainer('jdk8')
                    container('golang') {
                        sh '.ci/scripts/distribution/prepare-go.sh'
                    }
                }
            }
        }

        stage('Build Distribution') {
            environment {
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
            }
            steps {
                timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                    runMavenContainerCommand('.ci/scripts/distribution/build-java.sh')
                    container('maven') {
                        sh 'cp dist/target/zeebe-distribution-*.tar.gz zeebe-distribution.tar.gz'
                    }
                    stash name: "zeebe-build", includes: "m2-repository/io/zeebe/*/${VERSION}/*"
                    stash name: "zeebe-distro", includes: "zeebe-distribution.tar.gz"
                }
            }
        }

        stage('Build Docker Images') {
            environment {
                DOCKER_BUILDKIT = "1"
                IMAGE = "camunda/zeebe"
                TAG = 'current-test'
            }

            steps {
                timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                    container('docker') {
                        sh '.ci/scripts/docker/build.sh'
                        sh '.ci/scripts/docker/build_zeebe-hazelcast-exporter.sh'
                    }
                }
            }
        }

        stage('Verify') {
            parallel {
                stage('Analyse') {
                    steps {
                        timeout(time: longTimeoutMinutes, unit: 'MINUTES') {
                            runMavenContainerCommand('.ci/scripts/distribution/analyse-java.sh')
                        }
                    }
                }

                stage('BPMN TCK') {
                    steps {
                        timeout(time: longTimeoutMinutes, unit: 'MINUTES') {
                            runMavenContainerCommand('.ci/scripts/distribution/test-tck.sh')
                        }
                    }

                    post {
                        always {
                            junit testResults: "bpmn-tck/**/*/TEST*.xml", keepLongStdio: true
                        }
                    }
                }

                stage('Test (Go)') {
                    steps {
                        timeout(time: longTimeoutMinutes, unit: 'MINUTES') {
                            container('golang') {
                                sh '.ci/scripts/distribution/build-go.sh'
                            }

                            container('golang') {
                                sh '.ci/scripts/distribution/test-go.sh'
                            }
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST-go.xml", keepLongStdio: true
                        }
                    }
                }

                stage('Test (Java)') {
                    environment {
                        SUREFIRE_REPORT_NAME_SUFFIX = 'java-testrun'
                    }

                    steps {
                        timeout(time: longTimeoutMinutes, unit: 'MINUTES') {
                            runMavenContainerCommand('.ci/scripts/distribution/test-java.sh')
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}*.xml", keepLongStdio: true
                        }
                    }
                }

                stage('Test (Java 8)') {
                    environment {
                        SUREFIRE_REPORT_NAME_SUFFIX = 'java8-testrun'
                    }

                    steps {
                        timeout(time: longTimeoutMinutes, unit: 'MINUTES') {
                            runMavenContainerCommand('.ci/scripts/distribution/test-java8.sh', 'jdk8')
                        }
                    }

                    post {
                        always {
                            junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}*.xml", keepLongStdio: true
                        }
                    }
                }

                stage('IT') {
                    agent {
                        kubernetes {
                            cloud 'zeebe-ci'
                            label "zeebe-ci-build_${buildName}_it"
                            defaultContainer 'jnlp'
                            yamlFile '.ci/podSpecs/distribution.yml'
                        }
                    }

                    stages {
                        stage('Prepare') {
                            steps {
                                timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                                    prepareMavenContainer()
                                }
                            }
                        }

                        stage('Build Docker Image') {
                            environment {
                                DOCKER_BUILDKIT = "1"
                                IMAGE = "camunda/zeebe"
                                TAG = 'current-test'
                            }

                            steps {
                                timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                                    unstash name: "zeebe-distro"
                                    container('docker') {
                                        sh '.ci/scripts/docker/build.sh'
                                    }
                                }
                            }
                        }

                        stage('Test') {
                            environment {
                                SUREFIRE_REPORT_NAME_SUFFIX = 'it-testrun'
                            }

                            steps {
                                timeout(time: longTimeoutMinutes, unit: 'MINUTES') {
                                    unstash name: "zeebe-build"
                                    runMavenContainerCommand('.ci/scripts/distribution/it-java.sh')
                                }
                            }

                            post {
                                always {
                                    junit testResults: "**/*/TEST*${SUREFIRE_REPORT_NAME_SUFFIX}*.xml", keepLongStdio: true
                                }
                            }
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
            when {
                expression { params.RUN_QA }
            }
            environment {
                IMAGE = "gcr.io/zeebe-io/zeebe"
                VERSION = readMavenPom(file: 'parent/pom.xml').getVersion()
                TAG = "${env.VERSION}-${env.GIT_COMMIT}"
                DOCKER_GCR = credentials("zeebe-gcr-serviceaccount-json")
                ZEEBE_AUTHORIZATION_SERVER_URL = 'https://login.cloud.ultrawombat.com/oauth/token'
                ZEEBE_CLIENT_ID = 'W5a4JUc3I1NIetNnodo3YTvdsRIFb12w'
                QA_RUN_VARIABLES = "{\"zeebeImage\": \"${env.IMAGE}:${env.TAG}\", \"generationTemplate\": \"${params.GENERATION_TEMPLATE}\", \"channel\": \"Internal Dev\", " +
                                   "\"branch\": \"${env.BRANCH_NAME}\", \"build\": \"${currentBuild.absoluteUrl}\"}"
            }

            steps {
                timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                    container('docker') {
                        sh '.ci/scripts/docker/upload-gcr.sh'
                        withVault(
                        [ vaultSecrets:
                            [
                            [ path: 'secret/common/ci-zeebe/testbench-secrets-int',
                                secretValues:
                                [
                                    [envVar: 'ZEEBE_CLIENT_SECRET', vaultKey: 'clientSecret'],
                                    [envVar: 'ZEEBE_ADDRESS', vaultKey: 'contactPoint'],
                                ]
                            ],
                            ]
                        ]
                        ) {
                        sh '.ci/scripts/distribution/qa-testbench.sh'
                        }
                    }
                }

                input message: 'Were all QA tests successful?', ok: 'Yes'
            }
        }

        stage('Upload') {
            when { allOf { branch developBranchName; not { triggeredBy 'TimerTrigger' } } }
            steps {
                retry(3) {
                    timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
                        runMavenContainerCommand('.ci/scripts/distribution/upload.sh')
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
                            timeout(time: shortTimeoutMinutes, unit: 'MINUTES') {
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
                sendZeebeSlackMessage()
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
                if (!hasBuildResultChanged()) {
                    return
                }

                sendZeebeSlackMessage()
            }
        }
    }
}

//////////////////// Helper functions ////////////////////

def getMavenContainerNameForJDK(String jdk = null) {
    "maven${jdk ? '-' + jdk : ''}"
}

def prepareMavenContainer(String jdk = null) {
    container(getMavenContainerNameForJDK(jdk)) {
        sh '.ci/scripts/distribution/prepare.sh'
    }
}

def runMavenContainerCommand(String shellCommand, String jdk = null) {
    container(getMavenContainerNameForJDK(jdk)) {
        configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
            sh shellCommand
        }
    }
}

// TODO: can be extracted to zeebe-jenkins-shared-library
def setHumanReadableBuildDisplayName(int maximumLength = 45) {
    script {
        commit_summary = sh([returnStdout: true, script: 'git show -s --format=%s']).trim()
        displayNameFull = "#${env.BUILD_NUMBER}: ${commit_summary}"

        if (displayNameFull.length() <= maximumLength) {
            currentBuild.displayName = displayNameFull
        } else {
            displayStringHardTruncate = displayNameFull.take(maximumLength)
            currentBuild.displayName = displayStringHardTruncate.take(displayStringHardTruncate.lastIndexOf(' '))
        }
    }
}

// TODO: can be extracted to zeebe-jenkins-shared-library
def sendZeebeSlackMessage() {
    echo "Send slack message"
    slackSend(
        channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
        message: "Zeebe ${env.BRANCH_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
}
