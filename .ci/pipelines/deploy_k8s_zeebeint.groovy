#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { "agents-n1-standard-32-netssd-preempt" }
def static GCLOUD_DOCKER_IMAGE() { "gcr.io/google.com/cloudsdktool/cloud-sdk:alpine" }
def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.3-jdk-8-slim" }
static String kubectlAgent() {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL()}
  tolerations:
    - key: "${NODE_POOL()}"
      operator: "Exists"
      effect: "NoSchedule"
  serviceAccountName: ci-optimize-camunda-cloud
  volumes:
  - name: import
    emptyDir: {}
  containers:
  - name: gcloud
    image: ${GCLOUD_DOCKER_IMAGE()}
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 512Mi
      requests:
        cpu: 500m
        memory: 512Mi
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        value: 2
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 6
        memory: 6Gi
      requests:
        cpu: 6
        memory: 6Gi
"""
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml kubectlAgent()
    }
  }

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
  }

  stages {
    stage('Prepare') {
      steps {
        dir('infra-core') {
          git url: 'git@github.com:camunda/infra-core',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
        }

        dir('optimize') {
          optimizeCloneGitRepo(params.BRANCH)
        }

        container('gcloud') {
          sh('gcloud components install kubectl --quiet')
        }
      }
    }
    stage('Deploy to K8s') {
      steps {
        container('gcloud') {
          dir('optimize') {
            setBuildEnvVars()
          }

          dir('infra-core') {
            sh("""
              sed -i \
                -e "s/@CAMBPM_VERSION@/$CAMBPM_VERSION/g" \
                -e "s/@ES_VERSION@/$ES_VERSION/g" \
                ${WORKSPACE}/optimize/.ci/zeebeint-deployment/deployment.yml
            """)

            sh("""
              ./cmd/k8s/deploy-template-to-branch \
                ${WORKSPACE}/infra-core/camunda-ci-v2/deployments/optimize-branch \
                ${WORKSPACE}/optimize/.ci/zeebeint-deployment \
                ${params.BRANCH.toLowerCase().replaceAll(/[^a-z0-9-]/, '-')} \
                optimize
            """)
          }
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'infra-core/rendered-templates/**/*'
        }
      }
    }
    stage('Zeebe Data Generation') {
      steps {
        container('maven') {
          runMaven('-T\$LIMITS_CPU -pl backend -am -DskipTests -Dskip.fe.build -Dskip.docker clean install')
          runMaven('-f zeebe-data-generator clean compile exec:java')
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'infra-core/rendered-templates/**/*'
        }
      }
    }
  }

  post {
    changed {
      sendNotification(currentBuild.result,null,null,[[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])
    }
    always {
      // Retrigger the build if the agent node disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}
