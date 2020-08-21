final PROJECT = "zeebe"
final CHAOS_TEST_NAMESPACE = "zeebe-chaos-test"

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
            steps {
                container('python') {
                    // copy dummy kubeconfig so we can change default namespace later, without the config
                    // file `kubectl` cannot change the context since it's not defined
                    sh 'mkdir -p ~/.kube && cp zeebe/.ci/scripts/chaos-tests/kubeconfig ~/.kube/config'
                    dir('zeebe/benchmarks/setup') {
                      sh "./newBenchmark.sh ${CHAOS_TEST_NAMESPACE}"
                      dir("${CHAOS_TEST_NAMESPACE}") {
                        sh "cp -rv ../../../.ci/scripts/chaos-tests/kustomize ."
                        sh "helm template --release-name ${CHAOS_TEST_NAMESPACE} zeebe/zeebe-cluster -f zeebe-values.yaml --output-dir ."
                        sh "cp -v ${CHAOS_TEST_NAMESPACE}/zeebe-cluster/templates/*.yaml kustomize/"
                        sh "cp -v worker.yaml kustomize/"
                        sh "kubectl apply -k kustomize"
                      }
                    }
                }
            }
        }

        stage('Run chaos tests') {
            steps {
                container('python') {
                    dir('zeebe-chaos/chaos-experiments/kubernetes') {
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
              dir("zeebe/benchmarks/setup/") {
               sh "./deleteBenchmark.sh ${CHAOS_TEST_NAMESPACE}"
            }
            archiveArtifacts 'zeebe-chaos/chaos-experiments/kubernetes/chaostoolkit.log'
            archiveArtifacts 'zeebe-chaos/chaos-experiments/kubernetes/journal.json'
          }
        }

        changed {
            script {
                slackSend(
                    channel: "#zeebe-ci${jenkins.model.JenkinsLocationConfiguration.get()?.getUrl()?.contains('stage') ? '-stage' : ''}",
                    message: "Zeebe ${env.JOB_NAME} build ${currentBuild.absoluteUrl} changed status to ${currentBuild.currentResult}")
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
