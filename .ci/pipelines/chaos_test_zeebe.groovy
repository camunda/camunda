final PROJECT = "zeebe"
final CHAOS_TEST_NAMESPACE = "6897f1b0-2cdf-44c7-84df-61ae048b811e-zeebe"
final CONTEXT = "gke_camunda-cloud-240911_europe-west1-d_ultrachaos"

pipeline {
    options {
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
                dir('zeebe-chaos') {
                    git url: 'git@github.com:zeebe-io/zeebe-chaos',
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
            environment {
                TOKEN = credentials('zeebe-chaos-serviceaccount-token')
            }
            steps {
                container('python') {
                    // copy kubeconfig and add user with camunda cloud token
                    sh 'mkdir -p ~/.kube && cp zeebe/.ci/scripts/chaos-tests/kubeconfig ~/.kube/config'
                    sh 'cat ~/.kube/config'
                    // create user with auth token
                    sh "kubectl config set-credentials ${CONTEXT}-zeebe-chaos-token-user --token ${TOKEN}"
                    // Set context to use token user
                    sh "kubectl config set-context ${CONTEXT} --user ${CONTEXT}-zeebe-chaos-token-user"
                    // swtich namespace
                    sh "kubens ${CHAOS_TEST_NAMESPACE}"
                }
            }
        }

        stage('Run chaos tests') {
            environment {
                CHAOS_SETUP = "cloud"
            }
            steps {
                container('python') {
                    dir('zeebe-chaos/chaos-experiments/camunda-cloud') {
                        sh "kubectl apply -f worker.yaml"
                        sh "kubectl get pods"
                        script {
                            findFiles(glob: 'production-m/**/*.json').each {
                                sh 'PATH="$PATH:$(pwd)/../scripts/" chaos run ' + it

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
                dir('zeebe-chaos/chaos-experiments/camunda-cloud') {
                    sh "kubectl delete -f worker.yaml"
                }
                archiveArtifacts 'zeebe-chaos/chaos-experiments/camunda-cloud/chaostoolkit.log'
                archiveArtifacts 'zeebe-chaos/chaos-experiments/camunda-cloud/journal.json'
            }
        }

        changed {
            script {
                slackSend(
                        channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
                        message: "Zeebe :monkey: **${env.JOB_NAME}** build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
            }
        }

    }
}

static String pythonAgent() {
    def nodePool = 'agents-n1-standard-32-netssd-preempt'
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
