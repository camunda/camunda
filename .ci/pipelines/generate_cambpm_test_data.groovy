#!/usr/bin/env groovy

boolean slaveDisconnected() {
  return currentBuild.rawBuild.getLog(10000).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException|FlowInterruptedException).*/
}

// general properties for CI execution
def static NODE_POOL() { return "slaves-ssd-stable" }

def static NODE_POOL_SIMPLE_AGENT() { return "slaves" }

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

def static POSTGRES_DOCKER_IMAGE(String postgresVersion) { return "postgres:${postgresVersion}" }

def static CAMBPM_DOCKER_IMAGE(String cambpmVersion) {
    return "registry.camunda.cloud/camunda-bpm-platform-ee:${cambpmVersion}"
}

CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

static String dataGenerationAgent(env, postgresVersion = '9.6-alpine', cambpmVersion) {
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
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  securityContext:
    fsGroup: 1000
  volumes:
    - name: ssd-storage
      hostPath:
        path: /mnt/disks/array0
        type: Directory
  initContainers:
  - name: init-cleanup
    image: busybox
    command: ['sh', '-c', 'rm -fr /ssd-storage/*']
    volumeMounts:
    - name: ssd-storage
      mountPath: /ssd-storage
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 2
      requests:
        cpu: 2
        memory: 6Gi
    volumeMounts:
      - name: ssd-storage
        mountPath: /cambpm-storage
        subPath: cambpm-storage
      - name: ssd-storage
        mountPath: /export
        subPath: gcloud
  - name: postgres
    image: ${POSTGRES_DOCKER_IMAGE(postgresVersion)}
    env:
      - name: POSTGRES_USER
        value: camunda
      - name: POSTGRES_PASSWORD
        value: camunda
      - name: POSTGRES_DB
        value: engine
      - name: TZ
        value: Europe/Berlin
    args: ["-c", "max_connections=200", "-c", "effective_cache_size=4GB", "-c", "shared_buffers=2GB", "-c", "synchronous_commit=off", "-c", "log_statement=none", "-c", "checkpoint_timeout=600", "-c", "max_wal_size=30GB"]
    ports:
    - containerPort: 5432
      name: postgres
      protocol: TCP
    resources:
      limits:
        cpu: 12
      requests:
        cpu: 12
        memory: 64Gi
    volumeMounts:
        - name: ssd-storage
          mountPath: /var/lib/postgresql/data
          subPath: pgdata
        - name: ssd-storage
          mountPath: /export
          subPath: gcloud
  - name: cambpm
    image: ${CAMBPM_DOCKER_IMAGE(cambpmVersion)}
    env:
      - name: DB_DRIVER
        value: org.postgresql.Driver
      - name: DB_USERNAME
        value: camunda
      - name: DB_PASSWORD
        value: camunda
      - name: DB_URL
        value: jdbc:postgresql://localhost:5432/engine
      - name: DB_CONN_MAXACTIVE
        value: 500
      - name: DB_CONN_MAXIDLE
        value: 500
      - name: DB_CONN_MINIDLE
        value: 150
      - name: TZ
        value: Europe/Berlin
      - name: WAIT_FOR
        value: localhost:5432
      - name: JAVA_OPTS
        value: "-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70 -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark"
    resources:
      limits:
        cpu: 16
      requests:
        cpu: 16
        memory: 12Gi
    volumeMounts:
      - name: ssd-storage
        mountPath: /camunda/logs
        subPath: camunda-logs
  - name: gcloud
    image: google/cloud-sdk:alpine
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
      - name: ssd-storage
        mountPath: /export
        subPath: gcloud
"""
}

static String mavenAgent() {
    return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: ${NODE_POOL_SIMPLE_AGENT()}
  tolerations:
    - key: "${NODE_POOL_SIMPLE_AGENT()}"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
"""
}
/******** START PIPELINE *******/

pipeline {
    agent none
    environment {
        NEXUS = credentials("camunda-nexus")
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: '5'))
        timestamps()
        timeout(time: 168, unit: 'HOURS')
    }

    stages {
        stage('Retrieve Cambpm Version') {
            agent {
                kubernetes {
                    cloud 'optimize-ci'
                    label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
                    defaultContainer 'jnlp'
                    yaml mavenAgent()
                }
            }
            steps {
                cloneGitRepo()
                script {
                    env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: readMavenPom().getProperties().getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
                }
            }
        }
        stage('Data Generation') {
            agent {
                kubernetes {
                    cloud 'optimize-ci'
                    label "optimize-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
                    defaultContainer 'jnlp'
                    slaveConnectTimeout 600
                    yaml dataGenerationAgent(env, POSTGRES_VERSION, env.CAMBPM_VERSION)
                }
            }
            stages {
                stage('Prepare') {
                    steps {
                        cloneGitRepo()
                        container('postgres') {
                            sh("df -h /export /var/lib/postgresql/data")
                        }
                        container('gcloud') {
                            sh("apk add --no-cache jq")
                        }
                        container('maven') {
                            sh("apt-get update && apt-get install -y jq")
                        }
                    }
                }
                stage('Generate Data') {
                    steps {
                        container('maven') {
                            cloneGitRepo()
                            // Generate Data
                            configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
                                sh("""
                                  if [ "${USE_E2E_PRESETS}" = true ]; then
                                    mvn -T1C -B -s \$MAVEN_SETTINGS_XML -f qa/data-generation compile exec:java -Dexec.args="--removeDeployments false --numberOfProcessInstances \$(cat client/e2e_presets.json | jq -r .numberOfProcessInstances) --definitions \$(cat client/e2e_presets.json | jq -r .definitions)"
                                  else
                                    mvn -T1C -B -s \$MAVEN_SETTINGS_XML -f qa/data-generation compile exec:java -Dexec.args="--numberOfProcessInstances ${NUM_INSTANCES}"
                                  fi
                                """)
                            }
                        }
                    }
                }
                stage('Dump Data') {
                    steps {
                        container('postgres') {
                            script {
                                env.EXPECTED_NUMBER_OF_PROCESS_INSTANCES = sh(script: 'psql -qAt -h localhost -U camunda -d engine -c "select count(*) from act_hi_procinst;"', returnStdout: true).trim()
                                env.EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES = sh(script: 'psql -qAt -h localhost -U camunda -d engine -c "select count(*) from act_hi_actinst;"', returnStdout: true).trim()
                                env.EXPECTED_NUMBER_OF_USER_TASKS = sh(script: 'psql -qAt -h localhost -U camunda -d engine -c "select count(*) as total from act_hi_taskinst;"', returnStdout: true).trim()
                                env.EXPECTED_NUMBER_OF_VARIABLES = sh(script: 'psql -qAt -h localhost -U camunda -d engine -c "select count(*) from act_hi_varinst where var_type_ in (\'string\', \'double\', \'integer\', \'long\', \'short\', \'date\', \'boolean\' ) and CASE_INST_ID_  is  null;"', returnStdout: true).trim()
                                env.EXPECTED_NUMBER_OF_DECISION_INSTANCES = sh(script: 'psql -qAt -h localhost -U camunda -d engine -c "select count(*) from act_hi_decinst;"', returnStdout: true).trim()
                            }
                            // Export dump
                            sh("pg_dump -h localhost -U camunda -n public --format=c --file=\"/export/${SQL_DUMP}\" engine")
                        }
                    }
                }
                stage('Upload Data') {
                    steps {
                        container('gcloud') {
                            // Upload data
                            sh("""
                              if [ "${USE_E2E_PRESETS}" = true ]; then
                                gsutil -h "x-goog-meta-NUM_INSTANCES:\$(cat client/e2e_presets.json | jq -r .numberOfProcessInstances)" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_PROCESS_INSTANCES:\$EXPECTED_NUMBER_OF_PROCESS_INSTANCES" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES:\$EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_USER_TASKS:\$EXPECTED_NUMBER_OF_USER_TASKS" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_VARIABLES:\$EXPECTED_NUMBER_OF_VARIABLES" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_DECISION_INSTANCES:\$EXPECTED_NUMBER_OF_DECISION_INSTANCES" \
                                       cp "/export/${SQL_DUMP}" gs://optimize-data/
                              else
                                gsutil -h "x-goog-meta-NUM_INSTANCES:${NUM_INSTANCES}" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_PROCESS_INSTANCES:\$EXPECTED_NUMBER_OF_PROCESS_INSTANCES" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES:\$EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_USER_TASKS:\$EXPECTED_NUMBER_OF_USER_TASKS" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_VARIABLES:\$EXPECTED_NUMBER_OF_VARIABLES" \
                                       -h "x-goog-meta-EXPECTED_NUMBER_OF_DECISION_INSTANCES:\$EXPECTED_NUMBER_OF_DECISION_INSTANCES" \
                                       cp "/export/${SQL_DUMP}" gs://optimize-data/
                              fi
                                """)
                            // Cleanup
                            sh("rm /export/${SQL_DUMP}")
                        }
                    }
                }
            }
        }
    }

    post {
        changed {
            buildNotification(currentBuild.result)
        }
        always {
            // Retrigger the build if the slave disconnected
            script {
                if (slaveDisconnected()) {
                    build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
                }
            }
        }
    }
}

private void cloneGitRepo() {
    git url: 'git@github.com:camunda/camunda-optimize',
            branch: "$BRANCH",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false
}

void buildNotification(String buildStatus) {
    // build status of null means successful
    buildStatus = buildStatus ?: 'SUCCESS'

    String buildResultUrl = "${env.BUILD_URL}"
    if (env.RUN_DISPLAY_URL) {
        buildResultUrl = "${env.RUN_DISPLAY_URL}"
    }

    def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
    def body = "See: ${buildResultUrl}"
    def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

    emailext subject: subject, body: body, recipientProviders: recipients
}
