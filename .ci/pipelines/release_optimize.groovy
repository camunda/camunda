#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
static String NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }
static String MAVEN_DOCKER_IMAGE() { return "maven:3.6.3-jdk-8-slim" }
static String DIND_DOCKER_IMAGE() { return "docker:18.06-dind" }

static String PUBLIC_DOCKER_REGISTRY(boolean pushChanges) {
  return (pushChanges && !isStagingJenkins()) ?
    'registry.camunda.cloud' : 'stage.registry.camunda.cloud'
}
static String PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }
static String PUBLIC_DOCKER_IMAGE(boolean pushChanges) { return "${PUBLIC_DOCKER_REGISTRY(pushChanges)}/optimize-ee/optimize" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

static String DOWNLOADCENTER_GS_ENTERPRISE_BUCKET_NAME(boolean pushChanges) {
  return (pushChanges && !isStagingJenkins()) ?
    'downloads-camunda-cloud-enterprise-release' :
    'stage-downloads-camunda-cloud-enterprise-release'
}

static boolean isStagingJenkins() {
  return jenkins.model.Jenkins.getInstanceOrNull()?.getRootUrl()?.contains('stage') ?: false
}

static boolean isMajorOrMinorRelease(releaseVersion) {
  def version = releaseVersion.tokenize('.')
  def patchVersion = version[2]

  return !patchVersion.contains('-') && patchVersion == '0'
}

String calculatePreviousVersion(releaseVersion) {
  if (isMajorOrMinorRelease(releaseVersion)) {
    // only major / minor GA (.0) release versions will trigger an auto-update of previousVersion property.
    println 'Auto-updating previousVersion property as release version is a valid major/minor version.'
    return releaseVersion
  } else {
    println 'Not auto-updating previousVersion property as release version is not a valid major/minor version.'
    return ""
  }
}

static String mavenDindAgent() {
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
        cpu: 5
        memory: 6Gi
      requests:
        cpu: 5
        memory: 6Gi
  - name: docker
    image: ${DIND_DOCKER_IMAGE()}
    args: ["--storage-driver=overlay2"]
    securityContext:
      privileged: true
    tty: true
    resources:
      limits:
        cpu: 2
        memory: 1Gi
      requests:
        cpu: 2
        memory: 1Gi
  - name: gcloud
    image: google/cloud-sdk:slim
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

static String smoketestPodSpec(params, String cambpmVersion, String esVersion) {
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
    image: ${PUBLIC_DOCKER_IMAGE(params.PUSH_CHANGES)}:${params.RELEASE_VERSION}
    imagePullPolicy: Always
    env:
    - name: JAVA_OPTS
      value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
    - name: OPTIMIZE_CAMUNDABPM_REST_URL
      value: http://cambpm:8080/engine-rest
    - name: OPTIMIZE_ELASTICSEARCH_HOST
      value: localhost
    resources:
      limits:
        cpu: 1
        memory: 2Gi
      requests:
        cpu: 1
        memory: 2Gi
  - name: cambpm
    image: registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}
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
    image: docker.elastic.co/elasticsearch/elasticsearch-oss:${esVersion}
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
"""
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  String buildResultUrl = "${env.BUILD_URL}"
  if(env.RUN_DISPLAY_URL) {
    buildResultUrl = "${env.RUN_DISPLAY_URL}"
  }

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${buildResultUrl}"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

void runRelease(params) {
  def pushChanges = 'true'
  def skipDeploy = 'false'

  if (!params.PUSH_CHANGES) {
    pushChanges = 'false'
    skipDeploy='true'
  }
  configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
    sh("""
    mvn -DpushChanges=${pushChanges} -DskipTests -Prelease,engine-latest release:prepare release:perform \
    -Dtag=${params.RELEASE_VERSION} -DreleaseVersion=${params.RELEASE_VERSION} -DdevelopmentVersion=${
      params.DEVELOPMENT_VERSION
    } \
    --settings=\$MAVEN_SETTINGS_XML '-Darguments=--settings=${MAVEN_SETTINGS_XML} -DskipTests -DskipNexusStagingDeployMojo=${skipDeploy} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn' \
    -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
  """)
  }

  sh ("""
    # This is needed to not abort the job in case 'git diff' returns a status different from 0
    set +e
    # auto-update previousVersion property
    if [ ! -z \"\${PREVIOUS_VERSION}\" ]; then
      sed -i "s/project.previousVersion>.*</project.previousVersion>${params.RELEASE_VERSION}</g" pom.xml
      git add pom.xml

      git diff --staged --quiet
      if [ \$? -ne 0 ]; then
        git commit -m \"chore(release): update previousVersion to new release version ${params.RELEASE_VERSION}\"
        if [ \"${pushChanges}\" = \"true\" ]; then
          git push origin ${params.BRANCH}
        fi
      else
        echo "Release version ${params.RELEASE_VERSION} did not change. Nothing to commit."
      fi
    fi
  """)
}

/******** START PIPELINE *******/

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml mavenDindAgent()
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '0.0.0', description: 'Version to release. Applied to pom.xml and Git tag.')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '0.1.0-SNAPSHOT', description: 'Next development version.')
    string(name: 'BRANCH', defaultValue: 'master', description: 'The branch used for the release checkout.')
    booleanParam(name: 'PUSH_CHANGES', defaultValue: false, description: 'Should the changes be pushed to remote locations.')
  }

  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    skipDefaultCheckout(true)
    buildDiscarder(logRotator(numToKeepStr:'50', artifactNumToKeepStr: '3'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        git url: 'git@github.com:camunda/camunda-optimize',
            branch: "${params.BRANCH}",
            credentialsId: 'camunda-jenkins-github-ssh',
            poll: false

        script {
          def mavenProps = readMavenPom().getProperties()
          env.ES_VERSION = params.ES_VERSION ?: mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
          env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
        }

        container('maven') {
          sh ('''
            # install git and ssh
            apt-get update && \
            apt-get -y install git openssh-client

            # setup ssh for github
            mkdir -p ~/.ssh
            ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts

            # git config
            git config --global user.email "ci_automation@camunda.com"
            git config --global user.name "camunda-jenkins"
          ''')
        }
      }
    }
    stage('Release') {
      environment {
        PREVIOUS_VERSION = "${calculatePreviousVersion(params.RELEASE_VERSION)}"
      }
      steps {
        container('maven') {
          sshagent(['camunda-jenkins-github-ssh']) {
            runRelease(params)
          }
        }
      }
      post {
        always {
          archiveArtifacts(
              artifacts: "target/checkout/distro/target/*.zip,target/checkout/distro/target/*.tar.gz",
              onlyIfSuccessful: false
          )
        }
      }
    }
    stage('Upload to DownloadCenter storage bucket') {
      environment {
        // Use stage bucket when doing test release or when on a staging Jenkins
        TARGET_PATH = "${DOWNLOADCENTER_GS_ENTERPRISE_BUCKET_NAME(params.PUSH_CHANGES)}/optimize/${params.RELEASE_VERSION}/"
      }
      steps {
        container('gcloud') {
          withCredentials([file(credentialsId: 'downloadcenter_upload_gcloud_key', variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
            sh """#!/bin/bash -xe
            gcloud auth activate-service-account --key-file \${GOOGLE_APPLICATION_CREDENTIALS}
            gsutil cp target/checkout/distro/target/*.{tar.gz,zip} gs://${TARGET_PATH}
            """
          }
        }
      }
    }
    stage('Docker Image') {
      environment {
        VERSION = "${params.RELEASE_VERSION}"
        PUSH_CHANGES = "${params.PUSH_CHANGES}"
        GCR_REGISTRY = credentials('docker-registry-ci3')
        REGISTRY_CAMUNDA_CLOUD = credentials('registry-camunda-cloud')
        MAJOR_OR_MINOR = isMajorOrMinorRelease(params.RELEASE_VERSION)
      }
      steps {
        container('docker') {
          configFileProvider([configFile(fileId: 'maven-nexus-settings-local-repo', variable: 'MAVEN_SETTINGS_XML')]) {
            sh("""
            cp \$MAVEN_SETTINGS_XML settings.xml
            echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin
            echo '${REGISTRY_CAMUNDA_CLOUD}' | docker login -u ci-optimize ${PUBLIC_DOCKER_REGISTRY(params.PUSH_CHANGES)} --password-stdin

            docker build -t ${PROJECT_DOCKER_IMAGE()}:${VERSION} \
              --build-arg SKIP_DOWNLOAD=true \
              --build-arg VERSION=${VERSION} \
              --build-arg SNAPSHOT=false \
              .

            if [ "${PUSH_CHANGES}" = true ]; then
              docker push ${PROJECT_DOCKER_IMAGE()}:${VERSION}
            fi

            docker tag ${PROJECT_DOCKER_IMAGE()}:${VERSION} ${PUBLIC_DOCKER_IMAGE(params.PUSH_CHANGES)}:${VERSION}
            docker push ${PUBLIC_DOCKER_IMAGE(params.PUSH_CHANGES)}:${VERSION}

            if [ "${MAJOR_OR_MINOR}" = true ]; then
              docker tag ${PROJECT_DOCKER_IMAGE()}:${VERSION} ${PUBLIC_DOCKER_IMAGE(params.PUSH_CHANGES)}:latest
              docker push ${PUBLIC_DOCKER_IMAGE(params.PUSH_CHANGES)}:latest
            fi
          """)
          }
        }
      }
    }
    stage('Smoketest Docker Image') {
      agent {
        kubernetes {
          cloud 'optimize-ci'
          label "optimize-ci-build_smoke_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
          defaultContainer 'jnlp'
          yaml smoketestPodSpec(params, env.CAMBPM_VERSION, env.ES_VERSION)
        }
      }
      steps {
        container('maven') {
          sh("""#!/bin/bash -eux
          echo Giving Optimize some time to start up
          sleep 60
          echo Smoke testing if Optimize API can be reached
          curl -q -f http://localhost:8090/api/status | grep -q connectionStatus
          echo Smoke testing if Optimize Frontend resources are accessible
          curl -q -f http://localhost:8090/index.html | grep -q html
          """)
        }
      }
    }
    stage('Trigger release example repo job') {
      when {
        expression { params.RELEASE_EXAMPLE == true }
      }
      steps {
        build job: '/camunda-optimize-example-repo-release',
            parameters: [
                string(name: 'RELEASE_VERSION', value: "${params.RELEASE_VERSION}"),
                string(name: 'DEVELOPMENT_VERSION', value: "${params.DEVELOPMENT_VERSION}"),
                string(name: 'BRANCH', value: "master"),
            ],
        wait: false
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
        if (agentDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}
