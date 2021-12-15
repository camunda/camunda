// vim: set filetype=groovy:


def buildName = "${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"

pipeline {
    agent {
        kubernetes {
            cloud 'zeebe-ci'
            label "zeebe-ci-release_${buildName}"
            defaultContainer 'jnlp'
            yaml '''\
metadata:
  labels:
    agent: zeebe-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-stable
  tolerations:
    - key: "agents-n1-standard-32-netssd-stable"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
    - name: maven
      image: maven:3.6.0-jdk-11
      command: ["cat"]
      tty: true
      env:
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
        - name: JAVA_TOOL_OPTIONS
          value: |
            -XX:+UseContainerSupport
      resources:
        limits:
          cpu: 8
          memory: 32Gi
        requests:
          cpu: 8
          memory: 32Gi
    - name: golang
      image: golang:1.15.15
      command: ["cat"]
      tty: true
      resources:
        limits:
          cpu: 8
          memory: 8Gi
        requests:
          cpu: 8
          memory: 8Gi
'''
        }
    }

    environment {
        NEXUS = credentials('camunda-nexus')
        MAVEN_CENTRAL = credentials('maven_central_deployment_credentials')
        GPG_PASS = credentials('password_maven_central_gpg_signing_key')
        GPG_PUB_KEY = credentials('maven_central_gpg_signing_key_pub')
        GPG_SEC_KEY = credentials('maven_central_gpg_signing_key_sec')
        GITHUB_TOKEN = credentials('github-cloud-zeebe-app')
        RELEASE_VERSION = "${params.RELEASE_VERSION}"
        RELEASE_BRANCH = "release-${params.RELEASE_VERSION}"
        DEVELOPMENT_VERSION = "${params.DEVELOPMENT_VERSION}"
        PUSH_CHANGES = "${params.PUSH_CHANGES}"
        PUSH_DOCKER = "${params.PUSH_DOCKER}"
        SKIP_DEPLOY = "${!params.PUSH_CHANGES}"
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: '-1', numToKeepStr: '10'))
        skipDefaultCheckout()
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
    }

    stages {
        stage('Prepare') {
            steps {
                git url: 'https://github.com/camunda-cloud/zeebe.git',
                    branch: "${env.RELEASE_BRANCH}",
                    credentialsId: 'github-cloud-zeebe-app',
                    poll: false

                container('maven') {
                    sh '.ci/scripts/release/prepare.sh'
                }
                container('golang') {
                    sh '.ci/scripts/release/prepare-go.sh'
                }
            }
        }

        stage('Build') {
            steps {
                container('golang') {
                    sh '.ci/scripts/release/build-go.sh'
                }
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/release/build-java.sh'
                    }
                }
            }
        }

        stage('Maven Release') {
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/release/maven-release.sh'
                    }
                }
            }
        }

        stage('Update Compat Version') {
            steps {
                container('golang') {
                    sh '.ci/scripts/release/compat-update-go.sh'
                }
                container('maven') {
                    configFileProvider([configFile(fileId: 'maven-nexus-settings-zeebe', variable: 'MAVEN_SETTINGS_XML')]) {
                        sh '.ci/scripts/release/compat-update-java.sh'
                    }
                }
            }
        }


        stage('GitHub Release') {
            when { expression { return params.PUSH_CHANGES } }
            steps {
                container('maven') {
                    sh '.ci/scripts/release/github-release.sh'
                }

                container('golang') {
                    sh '.ci/scripts/release/post-github.sh'
                }
            }
        }

        stage('Docker Image') {
            when { expression { return params.PUSH_DOCKER } }
            steps {
                build job: 'zeebe-docker', parameters: [
                    string(name: 'BRANCH', value: env.RELEASE_BRANCH),
                    string(name: 'VERSION', value: params.RELEASE_VERSION),
                    booleanParam(name: 'IS_LATEST', value: params.IS_LATEST),
                    booleanParam(name: 'PUSH', value: true)
                ]
            }
        }
    }

    post {
        failure {
            slackSend(
                channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
                message: "Release job build ${currentBuild.absoluteUrl} failed!")
        }
    }
}
