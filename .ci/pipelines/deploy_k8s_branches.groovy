#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// general properties for CI execution
def static NODE_POOL() { "agents-n1-standard-32-netssd-preempt" }
def static GCLOUD_DOCKER_IMAGE() { "gcr.io/google.com/cloudsdktool/cloud-sdk:alpine" }
def static POSTGRES_DOCKER_IMAGE(String postgresVersion) { "postgres:${postgresVersion}" }
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
    volumeMounts:
    - name: import
      mountPath: /import
  - name: postgres
    image: ${POSTGRES_DOCKER_IMAGE(postgresVersion)}
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 512Mi
      requests:
        cpu: 500m
        memory: 512Mi
    env:
    - name: PGUSER
      value: camunda
    - name: PGPASSWORD
      value: camunda123
    - name: PGHOST
      value: stage-postgres.optimize
    - name: PGDATABASE
      value: optimize-ci-performance
    volumeMounts:
    - name: import
      mountPath: /import
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
          camundaInstallKubectl()
          sh("""
            apk add --no-cache jq py-pip && pip install yq
            gsutil stat gs://optimize-data/optimize_data-stage.sqlc | grep ETag |  yq -r '.ETag' >> /import/optimize_large_data-stage.etag || true
            gsutil cp   gs://optimize-data/optimize_large_data-stage.etag /import/optimize_large_data-stage.etag.old || true
            diff -Ns /import/optimize_large_data-stage.etag /import/optimize_large_data-stage.etag.new && touch /import/skip || true
          """)
        }
      }
    }
    stage('Import stage dataset') {
      steps {
        container('gcloud') {
          sh("""
            test -f /import/skip && echo "skipped" && exit 0
            gsutil cp gs://optimize-data/optimize_data-stage.sqlc /import/
          """)
        }
        container('postgres') {
          sh("""
            test -f /import/skip && echo "skipped" && exit 0
            pg_restore --if-exists --clean --jobs=24 --no-owner -d \$PGDATABASE /import/*.sqlc || echo pg_restore=\$?
          """)
        }
        container('gcloud') {
          sh("""
            test -f /import/skip && echo "skipped" && exit 0
            gsutil cp /import/*.etag gs://optimize-data/
          """)
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
                ${WORKSPACE}/optimize/.ci/branch-deployment/deployment.yml
            """)

            sh("""
              ./cmd/k8s/deploy-template-to-branch \
              ${WORKSPACE}/infra-core/camunda-ci/deployments/optimize-branch \
              ${WORKSPACE}/optimize/.ci/branch-deployment \
              ${params.BRANCH} \
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
    stage('Ingest Event Data') {
      steps {
        container('gcloud') {
          dir('optimize') {
            sh("""
              kubectl -n optimize-stage rollout status deployment/stage-optimize-camunda-cloud --watch=true
              kubectl -n optimize-stage port-forward deployment/stage-optimize-camunda-cloud 8090:8090 &
              while ! nc -z -w 3 localhost 8090; do
                sleep 5
              done
              # using -H 'Expect:' to disable 100-continue behavior of curl, see https://gms.tf/when-curl-sends-100-continue.html
              curl -v -X POST \
                http://localhost:8090/api/ingestion/event/batch \
                -H 'Expect:' \
                -H 'Content-Type: application/cloudevents-batch+json' \
                -H 'Authorization: secret' \
                -f --connect-timeout 5 --max-time 10 --retry 60 --retry-delay 0 --retry-max-time 600 --retry-all-errors\
                --data "@${WORKSPACE}/optimize/client/demo-data/eventIngestionBatch.json"
            """)
          }
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
