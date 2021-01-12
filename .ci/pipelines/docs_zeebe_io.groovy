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
    - name: debian
      image: debian:10
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
                container('debian') {
                    sh '.ci/scripts/docs/prepare.sh'
                }
            }
        }

        stage('Upload') {
            steps {
                container('debian') {
                    sshagent(['docs-zeebe-io-ssh']) {
                        sh '.ci/scripts/docs/upload.sh'
                    }
                }
            }
        }
    }
}
