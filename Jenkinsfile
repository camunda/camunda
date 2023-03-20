#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
// https://github.com/camunda/optimize-jenkins-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started
MAVEN_DOCKER_IMAGE = "maven:3.8.6-openjdk-11-slim"

// general properties for CI execution
static String NODE_POOL() { return "agents-n1-standard-16-netssd-stable" }
static String MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }
static String TEAM_DOCKER_IMAGE() { return 'registry.camunda.cloud/team-optimize/optimize' }
static String DOCKERHUB_IMAGE() { return 'camunda/optimize' }

static String getCamBpmDockerImage(String camBpmVersion) {
  return "registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${camBpmVersion}"
}

maintenanceBranchRegex = /^maintenance\/(?<version>\d+\.\d+)$/
mainBranchRegex = /^master$/


String storeNumOfBuilds() {
  isMainOrMaintenanceBranch() ? '30' : '10'
}

String storeNumOfArtifacts() {
  isMainOrMaintenanceBranch() ? '5' : '1'
}

ES_8_TEST_VERSION_POM_PROPERTY = "elasticsearch8.test.version"
ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
PREV_ES_TEST_VERSION_POM_PROPERTY = "previous.optimize.elasticsearch.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

/************************ START OF PIPELINE ***********************/

String basePodSpec(Integer mavenForkCount = 1, Integer mavenCpuLimit = 3, String mavenDockerImage = MAVEN_DOCKER_IMAGE) {
  // assuming 1Gig for each fork + management overhead
  String mavenMemoryLimit = mavenCpuLimit + 4
  return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-stable
  tolerations:
    - key: "agents-n1-standard-32-netssd-stable"
      operator: "Exists"
      effect: "NoSchedule"
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda/infra-core/tree/master/camunda-ci/deployments/optimize
      name: ci-optimize-cambpm-config
  - name: gcloud2postgres
    emptyDir: {}
  - name: es-snapshot
    emptyDir: {}
  - name: camunda-invoice-removal
    emptyDir: {}
  - name: docker-storage
    emptyDir: {}
  - name: es-it-data
    emptyDir:
      medium: Memory
      sizeLimit: 1Gi
  - name: dshm
    emptyDir:
      medium: Memory
  imagePullSecrets:
  - name: registry-camunda-cloud
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
        capabilities:
          add: ["IPC_LOCK", "SYS_RESOURCE"]
    - name: increase-the-ulimit
      image: busybox
      command: ["sh", "-c", "ulimit -n 65536"]
      securityContext:
        privileged: true
        capabilities:
          add: ["IPC_LOCK", "SYS_RESOURCE"]
  containers:
  - name: maven
    image: ${mavenDockerImage}
    command: ["cat"]
    tty: true
    volumeMounts:
      - mountPath: /dev/shm
        name: dshm
    env:
      - name: LIMITS_CPU
        value: ${mavenForkCount}
      - name: TZ
        value: Europe/Berlin
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    resources:
      limits:
        cpu: ${mavenCpuLimit}
        memory: ${mavenMemoryLimit}Gi
      requests:
        cpu: ${mavenCpuLimit}
        memory: ${mavenMemoryLimit}Gi
    """
}

String dockerInDockerSpec(Integer dockerCpuLimit = 4) {
  String dockerMemoryLimit = dockerCpuLimit + 4;
  return """
  - name: docker
    image: docker:20.10.5-dind
    args:
      - --storage-driver
      - overlay2
      - --ipv6
      - --fixed-cidr-v6
      - "2001:db8:1::/64"
    env:
      # The new dind versions expect secure access using cert
      # Setting DOCKER_TLS_CERTDIR to empty string will disable the secure access
      # (see https://hub.docker.com/_/docker?tab=description&page=1)
      - name: DOCKER_TLS_CERTDIR
        value: ""
    securityContext:
      privileged: true
    volumeMounts:
      - mountPath: /var/lib/docker
        name: docker-storage
    tty: true
    resources:
      limits:
        cpu: ${dockerCpuLimit}
        memory: ${dockerMemoryLimit}Gi
      requests:
        cpu: ${dockerCpuLimit}
        memory: ${dockerMemoryLimit}Gi
    """
}

String camBpmContainerSpec(String camBpmVersion, boolean usePostgres = false, Integer cpuLimit = 2, Integer memoryLimitInGb = 2, boolean deployDefaultInvoiceProcess = true) {
  String camBpmDockerImage = getCamBpmDockerImage(camBpmVersion)
  Integer jvmMemory = memoryLimitInGb - 1
  String additionalEnv = usePostgres ? """
      - name: DB_DRIVER
        value: "org.postgresql.Driver"
      - name: DB_USERNAME
        value: "camunda"
      - name: DB_PASSWORD
        value: "camunda"
      - name: DB_URL
        value: "jdbc:postgresql://localhost:5432/engine"
      - name: WAIT_FOR
        value: localhost:5432
  """ : ""
  String invoiceRemovalMount = deployDefaultInvoiceProcess ? "" : """
    - name: camunda-invoice-removal
      mountPath: /camunda/webapps/camunda-invoice
  """
  return """
  - name: cambpm
    image: ${camBpmDockerImage}
    imagePullPolicy: Always
    tty: true
    env:
      - name: JAVA_OPTS
        value: "-Xms${jvmMemory}g -Xmx${jvmMemory}g -XX:MaxMetaspaceSize=256m"
      - name: TZ
        value: Europe/Berlin
${additionalEnv}
    resources:
      limits:
        cpu: ${cpuLimit}
        memory: ${memoryLimitInGb}Gi
      requests:
        cpu: ${cpuLimit}
        memory: ${memoryLimitInGb}Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
${invoiceRemovalMount}
    """
}

String elasticSearchContainerSpec(String esVersion, Integer cpuLimit = 4, Integer memoryLimitInGb = 4) {
  String httpPort = "9200"
  Integer jvmMemory = memoryLimitInGb / 2
  return """
  - name: elasticsearch-${httpPort}
    image: docker.elastic.co/elasticsearch/elasticsearch:${esVersion}
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    resources:
      limits:
        cpu: ${cpuLimit}
        memory: ${memoryLimitInGb}Gi
      requests:
        cpu: ${cpuLimit}
        memory: ${memoryLimitInGb}Gi
    volumeMounts:
      - name: es-snapshot
        mountPath: /var/tmp
      - name: es-it-data
        mountPath: /opt/elasticsearch/volatile
    env:
      - name: ES_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
      - name: ES_JAVA_OPTS
        value: "-Xms${jvmMemory}g -Xmx${jvmMemory}g"
      - name: bootstrap.memory_lock
        value: true
      - name: discovery.type
        value: single-node
      - name: http.port
        value: ${httpPort}
      - name: cluster.name
        value: elasticsearch
      - name: xpack.security.enabled
        value: false
      - name: action.destructive_requires_name
        value: false
      # We usually run our integration tests concurrently, as some cleanup methods like #deleteAllOptimizeData
      # internally make usage of scroll contexts this lead to hits on the scroll limit.
      # Thus this increased scroll context limit.
      - name: search.max_open_scroll_context
        value: 1000
      - name: path.repo
        value: /var/tmp
      - name: path.data
        value: /opt/elasticsearch/volatile/data
      - name: path.logs
        value: /opt/elasticsearch/volatile/logs
   """
}

String elasticSearchUpgradeContainerSpec(String esVersion) {
  String httpPort = "9250"
  return """
  - name: elasticsearch-old
    image: docker.elastic.co/elasticsearch/elasticsearch:${esVersion}
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
    volumeMounts:
    - name: es-snapshot
      mountPath: /var/tmp
    env:
      - name: ES_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
      - name: ES_JAVA_OPTS
        value: "-Xms512m -Xmx512m"
      - name: bootstrap.memory_lock
        value: true
      - name: discovery.type
        value: single-node
      - name: http.port
        value: ${httpPort}
      - name: cluster.name
        value: elasticsearch
      - name: path.repo
        value: /var/tmp
   """
}

String smoketestPodSpec(String camBpmVersion, String esVersion) {
  return """---
apiVersion: v1
kind: Pod
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
  - name: registry-camunda-cloud

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
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 4
        memory: 3Gi
  - name: optimize
    securityContext:
      runAsUser: 1000620000
      runAsGroup: 0
    image: ${TEAM_DOCKER_IMAGE()}:${getImageTag()}
    imagePullPolicy: Always
    env:
    - name: OPTIMIZE_JAVA_OPTS
      value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
    - name: OPTIMIZE_ELASTICSEARCH_HOST
      value: localhost

    livenessProbe:
      initialDelaySeconds: 100
      periodSeconds: 10
      failureThreshold: 10
      httpGet:
        path: /
        port: 8090
    readinessProbe:
      initialDelaySeconds: 100
      periodSeconds: 10
      failureThreshold: 10
      httpGet:
        path: /api/readyz
        port: 8090
    resources:
      limits:
        cpu: 1
        memory: 2Gi
      requests:
        cpu: 1
        memory: 2Gi
  - name: cambpm
    image: registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${camBpmVersion}
    imagePullPolicy: Always
    tty: true
    env:
    - name: JAVA_OPTS
      value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
    - name: TZ
      value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 2Gi
      requests:
        cpu: 1
        memory: 2Gi
  - name: elasticsearch
    image: docker.elastic.co/elasticsearch/elasticsearch:${esVersion}
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK", "SYS_RESOURCE"]
    resources:
      limits:
        cpu: 1
        memory: 2Gi
      requests:
        cpu: 1
        memory: 2Gi
    env:
      - name: ES_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
      - name: ES_JAVA_OPTS
        value: "-Xms1g -Xmx1g"
      - name: bootstrap.memory_lock
        value: true
      - name: discovery.type
        value: single-node
      - name: http.port
        value: 9200
      - name: cluster.name
        value: elasticsearch
  - name: gcloud
    image: gcr.io/google.com/cloudsdktool/cloud-sdk:slim
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
"""
}

String upgradeTestPodSpec(String camBpmVersion, String esVersion, String prevEsVersion) {
  return basePodSpec(1, 2) +
      camBpmContainerSpec(camBpmVersion) +
      elasticSearchUpgradeContainerSpec(prevEsVersion) +
      elasticSearchContainerSpec(esVersion, 4, 8)
}

String itLatestPodSpec(String camBpmVersion, String esVersion) {
  return basePodSpec(4, 4) +
      camBpmContainerSpec(camBpmVersion, false, 4, 4) +
      elasticSearchContainerSpec(esVersion, 4, 8)
}

String itLatestPodSpecES8(String camBpmVersion, String esVersion) {
  return basePodSpec(4, 4) +
      camBpmContainerSpec(camBpmVersion, false, 6, 6) +
      elasticSearchContainerSpec(esVersion, 8, 16)
}

String itZeebePodSpec(String camBpmVersion, String esVersion) {
   return basePodSpec(6, 6) +
          camBpmContainerSpec(camBpmVersion, false, 2, 4) +
          elasticSearchContainerSpec(esVersion, 6, 8) +
          dockerInDockerSpec(12);
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yamlFile '.ci/podSpecs/builderAgent.yml'
    }
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
    def mavenProps = readMavenPom().getProperties()
    ES_VERSION = mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
    ES8_VERSION = mavenProps.getProperty(ES_8_TEST_VERSION_POM_PROPERTY)
    PREV_ES_VERSION = mavenProps.getProperty(PREV_ES_TEST_VERSION_POM_PROPERTY)
    CAMBPM_VERSION = mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: storeNumOfBuilds(), artifactNumToKeepStr: storeNumOfArtifacts()))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Build') {
      environment {
        VERSION = readMavenPom().getVersion()
      }
      steps {
        container('maven') {
          runMaven('install -Pengine-latest -Dskip.docker -DskipTests -T\$LIMITS_CPU')
        }
        stash name: "optimize-stash-client", includes: "client/build/**,client/src/**/*.css"
        stash name: "optimize-stash-backend", includes: "backend/target/*.jar,backend/target/lib/*"
        stash name: "optimize-stash-distro", includes: "m2-repository/org/camunda/optimize/camunda-optimize/*${VERSION}/*-production.tar.gz,m2-repository/org/camunda/optimize/camunda-optimize/*${VERSION}/*.xml,m2-repository/org/camunda/optimize/camunda-optimize/*${VERSION}/*.pom"
      }
    }
    stage('Unit tests') {
      parallel {
        stage('Backend') {
          steps {
            container('maven') {
              runMaven('test -Dskip.fe.build -Dskip.docker -T\$LIMITS_CPU')
            }
          }
          post {
            always {
              junit testResults: '**/surefire-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
            }
          }
        }
        stage('Frontend') {
          steps {
            container('node') {
              sh('''
                cd ./client
                yarn test:ci
              ''')
            }
          }
          post {
            always {
              junit testResults: 'client/jest-test-results.xml', allowEmptyResults: false, keepLongStdio: true
            }
          }
        }
      }
    }
    stage('Integration and migration tests, and static analysis') {
      environment {
        CAM_REGISTRY     = credentials('repository-camunda-cloud')
        SONARCLOUD_TOKEN = credentials('sonarcloud-token')
        GITHUB_TOKEN     = credentials("${optimizeUtils.defaultCredentialsId()}")
      }
      failFast false
      parallel {
        stage('SonarQube - Java') {
          when {
            not {
              expression {
                isMainOrMaintenanceBranch()
              }
            }
          }
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-static-analysis_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml basePodSpec(1, 3)
            }
          }
          steps {
            container('maven') {
              configFileProvider([configFile(
                fileId: 'maven-nexus-settings-local-repo',
                variable: 'MAVEN_SETTINGS_XML'
              )]) {
                // We need to at least test-compile as some modules like optimize-data-generator
                // make use of test classes of the backend module
                runMaven('test-compile -Dskip.fe.build -T\$LIMITS_CPU')
                sh '''
                  apt-get update && apt-get install -qq git
                  # This step is needed to fetch repo branches so SonarQube can diff them.
                  # It's used to scan and report only differences instead whole branch.
                  # TODO: Remove git config manipulation when project switched to SSH checkout.
                  git config --global --add safe.directory "\$PWD"
                  git config url."https://${GITHUB_TOKEN}@github.com/".insteadOf "https://github.com/"
                  .ci/scripts/sonarqube-mvn.sh
                '''
              }
            }
          }
        }
        stage('Migration') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-migration_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml upgradeTestPodSpec(env.CAMBPM_VERSION, env.ES_VERSION, env.PREV_ES_VERSION)
            }
          }
          steps {
            unstash name: "optimize-stash-distro"
            unstash name: "optimize-stash-client"
            migrationTestSteps()
          }
          post {
            always {
              junit testResults: 'upgrade/target/failsafe-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
              archiveArtifacts artifacts: 'qa/upgrade-tests/target/*.log', onlyIfSuccessful: false
            }
            failure {
              archiveArtifacts artifacts: 'qa/upgrade-tests/target/*.json', allowEmptyArchive: false
            }
          }
        }
        stage('Data upgrade test') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-data-upgrade_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml upgradeTestPodSpec(env.CAMBPM_VERSION, env.ES_VERSION, env.PREV_ES_VERSION)
            }
          }
          steps {
            unstash name: "optimize-stash-distro"
            dataUpgradeTestSteps()
          }
          post {
            always {
              junit testResults: 'qa/upgrade-tests/target/failsafe-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
              archiveArtifacts artifacts: 'qa/upgrade-tests/target/*.log', allowEmptyArchive: false, onlyIfSuccessful: false
            }
          }
        }
        stage('C7 IT Latest') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-latest_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml itLatestPodSpec(env.CAMBPM_VERSION, env.ES_VERSION)
            }
          }
          steps {
            unstash name: "optimize-stash-client"
            // Exclude all Zeebe tests
            integrationTestSteps('latest', 'Zeebe-test', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
            }
          }
        }
        stage('C7 IT Latest using ES8') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-latest_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml itLatestPodSpecES8(env.CAMBPM_VERSION, env.ES8_VERSION)
            }
          }
          steps {
            unstash name: "optimize-stash-client"
            // Exclude all Zeebe tests
            integrationTestSteps('latest', 'Zeebe-test', '')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
            }
          }
        }
        stage('C7 IT Zeebe Latest') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-zeebe_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml itZeebePodSpec(env.CAMBPM_VERSION, env.ES_VERSION)
            }
          }
          steps {
            unstash name: "optimize-stash-client"
            integrationTestSteps('latest', '', 'Zeebe-test')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
            }
          }
        }
        stage('C7 IT Zeebe Latest ES8') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-zeebe-es8_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml itZeebePodSpec(env.CAMBPM_VERSION, env.ES8_VERSION)
            }
          }
          steps {
            unstash name: "optimize-stash-client"
            integrationTestSteps('latest', '', 'Zeebe-test')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: false, keepLongStdio: true
            }
          }
        }
      }
    }
    stage('Deploy') {
      parallel {
        stage("Docker Operations") {
          when {
            expression {
              // first part of the expression covers pure branch builds,
              // the second covers PR builds where BRANCH_NAME is not available
              isMainOrMaintenanceBranch() || isMainOrMaintenanceBranch(CHANGE_BRANCH)
            }
          }
          stages {
            stage('Build Docker') {
              environment {
                CRED_REGISTRY_CAMUNDA_CLOUD = credentials('registry-camunda-cloud')
                DOCKERHUB_REGISTRY_CREDENTIALS = credentials('camunda-dockerhub')
                DOCKER_BRANCH_TAG = getBranchSlug()
                DOCKER_IMAGE_TEAM = TEAM_DOCKER_IMAGE()
                DOCKER_IMAGE_DOCKER_HUB = DOCKERHUB_IMAGE()
                DOCKER_LATEST_TAG = getLatestTag()
                DOCKER_TAG = getImageTag()
                PUSH_LATEST_TAG = "${isMainOrMaintenanceBranch() ? "TRUE" : "FALSE"}"
                VERSION = readMavenPom().getVersion()
                IS_MAIN = "${isMainBranch() ? "TRUE" : "FALSE"}"
                REVISION = "${env.GIT_COMMIT}"
                DATE = java.time.Instant.now().toString()
              }
              steps {
                container('docker') {
                  sh("""#!/bin/bash -eux
                  echo '${CRED_REGISTRY_CAMUNDA_CLOUD}' | docker login -u ci-optimize registry.camunda.cloud --password-stdin

                  tags=('${DOCKER_IMAGE_TEAM}:${DOCKER_TAG}' '${DOCKER_IMAGE_TEAM}:${DOCKER_BRANCH_TAG}')

                  if [ "${PUSH_LATEST_TAG}" = "TRUE" ]; then
                    tags+=('${DOCKER_IMAGE_TEAM}:${DOCKER_LATEST_TAG}')
                  fi

                  if [ "${IS_MAIN}" = "TRUE" ]; then
                    docker login --username ${DOCKERHUB_REGISTRY_CREDENTIALS_USR} --password ${DOCKERHUB_REGISTRY_CREDENTIALS_PSW}
                    tags+=('${DOCKER_IMAGE_DOCKER_HUB}:SNAPSHOT')
                  fi

                  printf -v tag_arguments -- "-t %s " "\${tags[@]}"
                  docker buildx create --use

                  export VERSION=${VERSION}
                  export DATE=${DATE}
                  export REVISION=${REVISION}
                  export BASE_IMAGE=docker.io/library/alpine:3
                  apk update
                  apk add jq

                  # Since docker buildx doesn't allow to use --load for a multi-platform build, we do it one at a time to be
                  # able to perform the checks before pushing
                  # First amd64
                  docker buildx build \
                    \${tag_arguments} \
                    --build-arg VERSION=${VERSION} \
                    --build-arg DATE=${DATE} \
                    --build-arg REVISION=${REVISION} \
                    --platform linux/amd64 \
                    --load \
                    .
                  export ARCHITECTURE=amd64
                  ./docker/test/verify.sh \${tags[@]}

                  # Now arm64
                  docker buildx build \
                    \${tag_arguments} \
                    --build-arg VERSION=${VERSION} \
                    --build-arg DATE=${DATE} \
                    --build-arg REVISION=${REVISION} \
                    --platform linux/arm64 \
                    --load \
                    .
                  export ARCHITECTURE=arm64
                  ./docker/test/verify.sh \${tags[@]}

                  # If we made it to here, all checks were successful. So let's build it to push. This is not as
                  # inefficient as it looks, since docker retrieves the previously generated images from the build cache
                  docker buildx build \
                    \${tag_arguments} \
                    --build-arg VERSION=${VERSION} \
                    --build-arg DATE=${DATE} \
                    --build-arg REVISION=${REVISION} \
                    --platform linux/amd64,linux/arm64 \
                    --push \
                    .
                  """)
                }
              }
            }
            stage('Smoketest Docker Image') {
              agent {
                kubernetes {
                  cloud 'optimize-ci'
                  inheritFrom "optimize-ci-build_smoke_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
                  defaultContainer 'jnlp'
                  yaml smoketestPodSpec(env.CAMBPM_VERSION, env.ES_VERSION)
                }
              }
              environment {
                LABEL = "optimize-ci-build_smoke_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              }
              steps {
                container('maven') {
                  sh("""#!/bin/bash -eux
                  echo Giving Optimize some time to start up
                  echo Smoke testing if Optimize is ready
                  curl -q -f -I http://localhost:8090/api/readyz | grep -q "200 OK"
                  echo Smoke testing if Optimize Frontend resources are accessible
                  curl -q -f http://localhost:8090/index.html | grep -q html
                  """)
                }
              }
              post {
                always {
                  container('gcloud') {
                    sh 'apt-get install kubectl'
                    sh 'kubectl logs -l jenkins/label=$LABEL -c elasticsearch > elasticsearch.log'
                    archiveArtifacts artifacts: 'elasticsearch.log', onlyIfSuccessful: false
                  }
                }
              }
            }
          }
        }
        stage('Deploy to Nexus') {
          when {
            expression {
              isMainOrMaintenanceBranch()
            }
          }
          steps {
            container('maven') {
              runMaven('deploy -Dskip.fe.build -DskipTests -Dskip.docker')
            }
          }
        }
      }
    }
  }

  post {
    changed {
      // Do not send email if the slave disconnected
      script {
        if (!agentDisconnected()) {
          sendNotification(currentBuild.result, null, null, [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])
        }
      }
    }
    always {
      // Retrigger the build if the slave disconnected
      script {
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

/************************ END OF PIPELINE ***********************/

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

String getBranchSlug() {
  return getBranchName().toLowerCase().replaceAll(/[^a-z0-9-]/, '-')
}

String getImageTag() {
  if (isMainOrMaintenanceBranch()) {
    return getGitCommitHash()
  }

  "branch-${getBranchSlug()}"
}

String getBranchName() {
  return isMainOrMaintenanceBranch() ? env.BRANCH_NAME : env.CHANGE_BRANCH
}

void integrationTestSteps(String engineVersion = 'latest', String excludedGroups = '', String includedGroups = '') {
  container('maven') {
    runMaven("verify -Dit.test.excludedGroups=${excludedGroups} -Dit.test.includedGroups=${includedGroups} -Dskip.docker -Dskip.fe.build -Pit,engine-${engineVersion} -pl backend -am -T\$LIMITS_CPU")
  }
}

void migrationTestSteps() {
  container('maven') {
    runMaven("install -Dskip.docker -Dskip.fe.build -DskipTests -pl backend,upgrade,qa/engine-it-plugin,qa/data-generation,qa/optimize-data-generator -am -Pengine-latest")
    runMaven("verify -Dskip.docker -pl upgrade")
    runMaven("verify -Dskip.docker -pl util/optimize-reimport-preparation -Pengine-latest,it")
    runMaven("verify -Dskip.docker -pl qa/upgrade-tests -Pupgrade-es-schema-tests")
  }
}

void dataUpgradeTestSteps() {
  container('maven') {
    runMaven("install -Dskip.docker -Dskip.fe.build -DskipTests -pl backend,upgrade,qa/data-generation,qa/optimize-data-generator -am -Pengine-latest")
    runMaven("verify -Dskip.docker -Dskip.fe.build -f qa/upgrade-tests -Pupgrade-optimize-data")
  }
}

void runMaven(String cmd) {
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("mvn ${cmd} -s \$MAVEN_SETTINGS_XML -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
  }
}

Boolean isMaintenanceBranch(String branchToMatch = env.BRANCH_NAME) {
  return (branchToMatch =~ maintenanceBranchRegex) as Boolean
}

boolean isMainBranch(String branchToMatch = env.BRANCH_NAME) {
  branchToMatch ==~ mainBranchRegex
}

String getMaintenanceVersion() {
  java.util.regex.Matcher matcher = (env.BRANCH_NAME =~ maintenanceBranchRegex)
  if (matcher) {
    matcher.matches()
    return matcher.group('version')
  }
}

String getLatestTag() {
  return isMaintenanceBranch() ? "${getMaintenanceVersion()}-latest" : "latest"
}

boolean isMainOrMaintenanceBranch(branchToMatch = env.BRANCH_NAME) {
  isMaintenanceBranch(branchToMatch) || isMainBranch(branchToMatch)
}
