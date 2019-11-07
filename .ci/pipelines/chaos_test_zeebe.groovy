final PROJECT = "zeebe"
final CHAOS_TEST_NAMESPACE = "zeebe-chaos-test"
final NOTIFY_EMAIL = "christopher.zell@camunda.com"

pipeline {
    triggers {
        cron('H 1 * * *')
    }

    options {
        buildDiscarder(
                logRotator(
                        numToKeepStr: '10',
                        daysToKeepStr: '-1',
                        artifactDaysToKeepStr: '-1',
                        artifactNumToKeepStr: '10'))
        timeout(time: 1, unit: 'HOURS')
        skipDefaultCheckout()
        timestamps()
    }

    agent {
        kubernetes {
            cloud "${PROJECT}-ci"
            label "${PROJECT}-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
            defaultContainer 'jnlp'
            yaml pythonAgent()
        }
    }

    stages {
        stage('Clone') {
            steps {
                dir('zeebe-benchmark') {
                    git url: 'git@github.com:zeebe-io/zeebe-benchmark',
                            branch: "master",
                            credentialsId: 'camunda-jenkins-github-ssh',
                            poll: false
                }
                dir('zeebe') {
                    git url: 'git@github.com:zeebe-io/zeebe',
                            branch: "develop",
                            credentialsId: 'camunda-jenkins-github-ssh',
                            poll: false
                }
            }
        }

        stage('Install dependencies') {
            steps {
                container('python') {
                    sh "zeebe/.ci/scripts/chaos-tests/install_deps.sh"
                }
            }
        }

        stage('Create cluster') {
            steps {
                container('python') {
                    // copy dummy kubeconfig so we can change default namespace later, without the config
                    // file `kubectl` cannot change the context since it's not defined
                    sh 'mkdir -p ~/.kube && cp zeebe/.ci/scripts/chaos-tests/kubeconfig ~/.kube/config'
                    sh "cp -a zeebe-benchmark/k8s/. zeebe/.ci/scripts/chaos-tests/kustomize/"

                    dir('zeebe/.ci/scripts/chaos-tests/') {
                        sh "./setup.sh --action=create --namespace=${CHAOS_TEST_NAMESPACE}"
                        sh "kubectl config set-context --current --namespace=${CHAOS_TEST_NAMESPACE}"
                        sh 'kubectl apply --kustomize kustomize'
                    }
                }
            }
        }

        stage('Run chaos tests') {
            steps {
                container('python') {
                    dir('zeebe-benchmark/chaos') {
                        script {
                            findFiles(glob: '**/*.json').each {
                                sh 'PATH="$PATH:$(pwd)/scripts/" chaos run ' + it
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            container('python') {
                sh "zeebe/.ci/scripts/chaos-tests/setup.sh --action=delete --namespace=${CHAOS_TEST_NAMESPACE}"
            }
        }

        failure {
            buildNotification(currentBuild.result, NOTIFY_EMAIL)
        }
    }
}

void buildNotification(String buildStatus, String to) {
    buildStatus = buildStatus ?: 'SUCCESS'

    String subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
    String body = "See: ${env.BUILD_URL}consoleFull"

    emailext subject: subject, body: body, to: to
}

static String pythonAgent() {
    def nodePool = 'slaves'
    def project = 'zeebe'
    """
metadata:
  labels:
    agent: ${project}-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${nodePool}
  tolerations:
    - key: "${nodePool}"
      operator: "Exists"
      effect: "NoSchedule"
  serviceAccountName: ci-${project}-camunda-cloud
  containers:
  - name: python
    image: python:3.8-alpine
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 200m
        memory: 128Mi
"""
}
