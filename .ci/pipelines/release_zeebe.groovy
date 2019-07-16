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
    cloud.google.com/gke-nodepool: slaves
  tolerations:
    - key: "slaves"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
    - name: maven
      image: maven:3.6.0-jdk-8
      command: ["cat"]
      tty: true
      env:
        - name: LIMITS_CPU
          valueFrom:
            resourceFieldRef:
              resource: limits.cpu
        - name: JAVA_TOOL_OPTIONS
          value: |
            -XX:+UnlockExperimentalVMOptions
            -XX:+UseCGroupMemoryLimitForHeap
      resources:
        limits:
          cpu: 2
          memory: 1Gi
        requests:
          cpu: 2
          memory: 1Gi
    - name: golang
      image: golang:1.12.2
      command: ["cat"]
      tty: true
      resources:
        limits:
          cpu: 2
          memory: 1Gi
        requests:
          cpu: 2
          memory: 1Gi
'''
      }
    }

    environment {
      NEXUS = credentials('camunda-nexus')
      MAVEN_CENTRAL = credentials('maven_central_deployment_credentials')
      GPG_PASS = credentials('password_maven_central_gpg_signing_key')
      GPG_PUB_KEY = credentials('maven_central_gpg_signing_key_pub')
      GPG_SEC_KEY = credentials('maven_central_gpg_signing_key_sec')
      GITHUB_TOKEN = credentials('camunda-jenkins-github')
      RELEASE_VERSION = "${params.RELEASE_VERSION}"
      RELEASE_BRANCH = "release-${params.RELEASE_VERSION}"
      DEVELOPMENT_VERSION = "${params.DEVELOPMENT_VERSION}"
      PUSH_CHANGES = "${params.PUSH_CHANGES}"
      PUSH_DOCKER = "${params.PUSH_DOCKER}"
      PUSH_DOCS = "${params.PUSH_DOCS}"
      SKIP_DEPLOY = "${!params.PUSH_CHANGES}"
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: '-1', numToKeepStr: '10'))
        skipDefaultCheckout()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Prepare') {
            steps {
                git url: 'git@github.com:zeebe-io/zeebe',
                    branch: "${env.RELEASE_BRANCH}",
                    credentialsId: 'camunda-jenkins-github-ssh',
                    poll: false

                container('maven') {
                    sh '.ci/scripts/release/prepare.sh'
                    sh '.ci/scripts/release/changelog.sh'
                }
            }
        }

        stage('Build') {
            steps {
                container('golang') {
                    sh '.ci/scripts/release/build-go.sh'
                }
                container('maven') {
                    sh '.ci/scripts/release/build-java.sh'
                }
            }
        }

        stage('Maven Release') {
            steps {
                container('maven') {
                    sshagent(['camunda-jenkins-github-ssh']) {
                        sh '.ci/scripts/release/maven-release.sh'
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
            }
        }

        stage('Docker Image') {
            when { expression { return params.PUSH_DOCKER } }
            steps {
                build job: 'zeebe-docker', parameters: [
                    string(name: 'BRANCH', value: env.RELEASE_BRANCH),
                    string(name: 'VERSION', value: params.RELEASE_VERSION),
                    booleanParam(name: 'IS_LATEST', value: params.IS_LATEST)
                ]
            }
        }

        stage('Publish Docs') {
            when { expression { return params.PUSH_DOCS } }
            steps {
                build job: 'zeebe-docs', parameters: [
                    string(name: 'BRANCH', value: env.RELEASE_BRANCH),
                    booleanParam(name: 'LIVE', value: true)
                ]
            }
        }
    }
}
