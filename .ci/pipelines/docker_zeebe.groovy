pipeline {

    agent {
        kubernetes {
            cloud 'zeebe-ci'
            label "zeebe-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
            defaultContainer 'jnlp'
            yaml '''\
metadata:
  labels:
    agent: zeebe-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-preempt
  tolerations:
    - key: "agents-n1-standard-32-netssd-preempt"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
    - name: maven
      image: maven:3.8.3-eclipse-temurin-17
      command: ["cat"]
      tty: true
      resources:
        limits:
          cpu: 500m
          memory: 1Gi
        requests:
          cpu: 500m
          memory: 1Gi
    - name: docker
      image: docker:18.09.4-dind
      args: ["--storage-driver=overlay2"]
      securityContext:
        privileged: true
      tty: true
      resources:
        limits:
          cpu: 1
          memory: 1Gi
        requests:
          cpu: 500m
          memory: 512Mi
'''
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
    }

    environment {
        DOCKER_HUB = credentials("camunda-dockerhub")
        VERSION = "${params.VERSION}"
        IS_LATEST = "${params.IS_LATEST}"
        PUSH = "${params.PUSH}"
        IMAGE = "camunda/zeebe"
        TAG = docker_tag("${params.VERSION}")
    }

    stages {
        stage('Prepare') {
            steps {
                git url: 'https://github.com/camunda-cloud/zeebe.git',
                        branch: "${params.BRANCH}",
                        credentialsId: 'github-cloud-zeebe-app',
                        poll: false

                container('maven') {
                    sh '.ci/scripts/docker/prepare.sh'
                }
            }
        }

        stage('Build') {
            steps {
                container('docker') {
                    sh '.ci/scripts/docker/build.sh'
                }
            }
        }

        stage('Upload') {
            when { environment name: 'PUSH', value: 'true' }
            steps {
                container('docker') {
                    sh '.ci/scripts/docker/upload.sh'
                }
            }
        }
    }
}

static String docker_tag(String version) {
    return version.endsWith('-SNAPSHOT') ? 'SNAPSHOT' : version
}
