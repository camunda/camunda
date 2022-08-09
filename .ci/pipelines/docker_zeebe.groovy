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
      image: maven:3.8.4-eclipse-temurin-17
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
      image: docker:20.10.5-dind
      args:
        - --storage-driver
        - overlay2
      env:
        # The new dind versions expect secure access using cert
        # Setting DOCKER_TLS_CERTDIR to empty string will disable the secure access
        # (see https://hub.docker.com/_/docker?tab=description&page=1)
        - name: DOCKER_TLS_CERTDIR
          value: ""
      securityContext:
        privileged: true
      tty: true
      resources:
        limits:
          cpu: 4
          memory: 4Gi
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
        REVISION = "${params.REVISION}"
        DATE = "${param.DATE}"
        VERIFY = "${param.VERIFY}"
    }

    stages {
        stage('Prepare') {
            steps {
                git url: 'https://github.com/camunda/zeebe.git',
                        branch: "${params.BRANCH}",
                        credentialsId: 'github-camunda-zeebe-app',
                        poll: false

                container('maven') {
                    sh '.ci/scripts/docker/prepare.sh'
                }

                // extract to a script if we need to do more here; these are necessary for the
                // verify script
                container('docker') {
                    sh 'apk add -q bash jq'
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

        stage('Verify') {
            when { environment name: 'VERIFY', value: 'true' }
            steps {
                container('docker') {
                    sh "./docker/test/verify.sh '${env.IMAGE}:${env.TAG}'"
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
