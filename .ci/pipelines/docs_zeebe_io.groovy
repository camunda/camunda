pipeline {
    agent {
        kubernetes {
            cloud 'zeebe-ci'
                label "zeebe-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
                defaultContainer 'jnlp'
                yaml '''\
apiVersion: v1
kind: Pod
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
    - name: alpine
      image: alpine:3.9.2
      command: ["cat"]
      tty: true
      resources:
        limits:
          cpu: 0.5
          memory: 512Mi
        requests:
          cpu: 0.5
          memory: 512Mi
'''
        }
    }

    environment {
        BRANCH = "${params.BRANCH}"
        LIVE = "${params.LIVE}"
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: '-1', numToKeepStr: '10'))
        skipDefaultCheckout()
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
    }

    stages {
        stage('Prepare') {
            steps {
                git url: 'git@github.com:zeebe-io/zeebe', branch: "${params.BRANCH}", credentialsId: 'camunda-jenkins-github-ssh', poll: false
                container('alpine') {
                    sh '.ci/scripts/docs/prepare.sh'
                }
            }
        }

        stage('Build') {
            steps {
                container('alpine') {
                    sh '.ci/scripts/docs/build.sh'
                }
            }
        }

        stage('Upload') {
            steps {
                container('alpine') {
                    sshagent(['docs-zeebe-io-ssh']) {
                        sh '.ci/scripts/docs/upload.sh'
                    }
                }
            }
        }
    }
}
