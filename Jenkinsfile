#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
// https://github.com/camunda/optimize-jenkins-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

MAVEN_DOCKER_IMAGE = "maven:3.8.1-jdk-11-slim"

static String PROJECT_DOCKER_IMAGE() { return 'gcr.io/ci-30-162810/camunda-optimize' }
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
    cloud.google.com/gke-nodepool: agents-n1-standard-32-netssd-preempt
  tolerations:
    - key: "agents-n1-standard-32-netssd-preempt"
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

String postgresContainerSpec() {
  return """
  - name: postgresql
    image: postgres:11.2
    env:
      - name: POSTGRES_USER
        value: camunda
      - name: POSTGRES_PASSWORD
        value: camunda
      - name: POSTGRES_DB
        value: engine
    resources:
      limits:
        cpu: 1
        memory: 512Mi
      requests:
        cpu: 1
        memory: 512Mi
    volumeMounts:
    - name: gcloud2postgres
      mountPath: /db_dump/
  """
}

String gcloudContainerSpec() {
  return """
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
    volumeMounts:
    - name: gcloud2postgres
      mountPath: /db_dump/
  """
}

String upgradeTestPodSpec(String camBpmVersion, String esVersion, String prevEsVersion) {
  return basePodSpec(1, 2) +
      camBpmContainerSpec(camBpmVersion) +
      elasticSearchUpgradeContainerSpec(prevEsVersion) +
      elasticSearchContainerSpec(esVersion, 2, 2)
}

String itLatestPodSpec(String camBpmVersion, String esVersion) {
  return basePodSpec(8, 10) +
      camBpmContainerSpec(camBpmVersion, false, 6, 4) +
      elasticSearchContainerSpec(esVersion, 8, 8)
}

String itZeebePodSpec(String camBpmVersion, String esVersion) {
   return basePodSpec(6, 6) +
          camBpmContainerSpec(camBpmVersion, false, 2, 4) +
          elasticSearchContainerSpec(esVersion, 6, 6) +
          dockerInDockerSpec(12);
}

String e2eTestPodSpec(String camBpmVersion, String esVersion) {
  // use Docker image with preinstalled headless Chrome and Maven
  return basePodSpec(1, 6, 'markhobson/maven-chrome:jdk-11') +
      camBpmContainerSpec(camBpmVersion, true, 2, 2, false) +
      elasticSearchContainerSpec(esVersion) +
      postgresContainerSpec() +
      gcloudContainerSpec()
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
        stage('IT Latest CamBPM') {
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
        stage('IT Latest Zeebe') {
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
        stage('E2E') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-e2e_${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml e2eTestPodSpec(env.CAMBPM_VERSION, env.ES_VERSION)
            }
          }
          steps {
            unstash name: 'optimize-stash-client'
            unstash name: 'optimize-stash-backend'
            e2eTestSteps()
          }
          post {
            always {
              archiveArtifacts artifacts: 'client/build/*.log'
            }
          }
        }
      }
    }
    stage('Deploy') {
      parallel {
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
        stage('Build Docker') {
          when {
            expression {
              // first part of the expression covers pure branch builds,
              // the second covers PR builds where BRANCH_NAME is not available
              isMainOrMaintenanceBranch() || isMainOrMaintenanceBranch(CHANGE_BRANCH)
            }
          }
          environment {
            CRED_GCR_REGISTRY = credentials('docker-registry-ci3')
            CRED_REGISTRY_CAMUNDA_CLOUD = credentials('registry-camunda-cloud')
            DOCKERHUB_REGISTRY_CREDENTIALS = credentials('camunda-dockerhub')
            DOCKER_BRANCH_TAG = getBranchSlug()
            DOCKER_IMAGE = PROJECT_DOCKER_IMAGE()
            DOCKER_IMAGE_TEAM = TEAM_DOCKER_IMAGE()
            DOCKER_IMAGE_DOCKER_HUB = DOCKERHUB_IMAGE()
            DOCKER_LATEST_TAG = getLatestTag()
            DOCKER_TAG = getImageTag()
            PUSH_LATEST_TAG = "${isMainOrMaintenanceBranch() ? "TRUE" : "FALSE"}"
            SNAPSHOT = readMavenPom().getVersion().contains('SNAPSHOT')
            VERSION = readMavenPom().getVersion().replace('-SNAPSHOT', '')
            IS_MAIN = "${isMainBranch() ? "TRUE" : "FALSE"}"
          }
          steps {
            container('docker') {
              sh("""
              echo '${CRED_GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin
              echo '${CRED_REGISTRY_CAMUNDA_CLOUD}' | docker login -u ci-optimize registry.camunda.cloud --password-stdin

              docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} \
                --build-arg SKIP_DOWNLOAD=true \
                --build-arg VERSION=${VERSION} \
                --build-arg SNAPSHOT=${SNAPSHOT} \
                .

              docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
              docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE_TEAM}:${DOCKER_BRANCH_TAG}
              docker push ${DOCKER_IMAGE_TEAM}:${DOCKER_BRANCH_TAG}

              if [ "${PUSH_LATEST_TAG}" = "TRUE" ]; then
                docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:${DOCKER_LATEST_TAG}
                docker push ${DOCKER_IMAGE}:${DOCKER_LATEST_TAG}
              fi

              if [ "${IS_MAIN}" = "TRUE"]; then
                docker login --username ${DOCKERHUB_REGISTRY_CREDENTIALS_USR} --password ${DOCKERHUB_REGISTRY_CREDENTIALS_PSW}
                docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE_DOCKER_HUB}:SNAPSHOT
                docker push ${DOCKER_IMAGE_DOCKER_HUB}:SNAPSHOT
              fi
              """)
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

void e2eTestSteps() {
  container('gcloud') {
    sh 'gsutil -q -m cp gs://optimize-data/optimize_data-e2e.sqlc /db_dump/dump.sqlc'
  }
  container('postgresql') {
    sh 'pg_restore --clean --if-exists -v -h localhost -U camunda -d engine /db_dump/dump.sqlc || true'
  }
  container('maven') {
    runMaven('test -pl client -Pclient.e2etests-chromeheadless -Dskip.yarn.build')
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
