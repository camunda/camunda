#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
// https://github.com/camunda/optimize-jenkins-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { "agents-n1-standard-32-netssd-preempt" }
def static GCLOUD_DOCKER_IMAGE() { "gcr.io/google.com/cloudsdktool/cloud-sdk:alpine" }
static String kubectlAgent(postgresVersion='9.6-alpine') {
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
"""
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      serviceAccount 'ci-optimize-camunda-cloud'
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
          git url: 'https://github.com/camunda/infra-core',
            branch: "${params.INFRASTRUCTURE_BRANCH}",
            credentialsId: optimizeUtils.defaultCredentialsId(),
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
                ${WORKSPACE}/infra-core/camunda-ci/deployments/optimize-branch \
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
  }

  post {
    changed {
      sendEmailNotification()
    }
    always {
      retriggerBuildIfDisconnected()
    }
  }
}
