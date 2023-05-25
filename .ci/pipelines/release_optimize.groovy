#!/usr/bin/env groovy

// https://github.com/camunda/jenkins-global-shared-library
// https://github.com/camunda/optimize-jenkins-shared-library
@Library(["camunda-ci", "optimize-jenkins-shared-library"]) _

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
static String NODE_POOL() { return "agents-n1-standard-32-netssd-stable" }

static String MAVEN_DOCKER_IMAGE() { return "maven:3.8.6-openjdk-11-slim" }

static String DIND_DOCKER_IMAGE() { return "docker:20.10.16-dind" }

static String DOCKER_REGISTRY(boolean pushChanges) {
  return (pushChanges && !isStagingJenkins()) ?
    'registry.camunda.cloud' : 'stage.registry.camunda.cloud'
}

static String DOCKER_REGISTRY_IMAGE(boolean pushChanges) {
  return "${DOCKER_REGISTRY(pushChanges)}/optimize-ee/optimize"
}

static String DOCKERHUB_IMAGE() { return "camunda/optimize" }

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
  # this docker-in-docker container is used to spawn containers dynamically inside a docker container
  - name: dind
    image: ${DIND_DOCKER_IMAGE()}
    args: ["--storage-driver=overlay2"]
    securityContext:
      privileged: true
    env:
      # This disabled automatic TLS setup as this is not exposed to the network anyway
      - name: DOCKER_TLS_CERTDIR
        value: ""
    tty: true
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
  # this docker image is used for docker cli as it ships with additional tooling like buildx for multiplatform builds
  - name: docker
    image: crazymax/docker:20.10.16
    command: ["/bin/bash"]
    env:
      - name: DOCKER_HOST
        value: tcp://localhost:2375
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 256Mi
      requests:
        cpu: 500m
        memory: 256Mi
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
    securityContext:
      runAsUser: 1000620000
      runAsGroup: 0
    image: ${DOCKER_REGISTRY_IMAGE(params.PUSH_CHANGES)}:${params.RELEASE_VERSION}
    imagePullPolicy: Always
    env:
    - name: OPTIMIZE_JAVA_OPTS
      value: "-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m"
    - name: OPTIMIZE_ELASTICSEARCH_HOST
      value: localhost
    livenessProbe:
      initialDelaySeconds: 50
      periodSeconds: 10
      failureThreshold: 10
      httpGet:
        path: /
        port: 8090
    readinessProbe:
      initialDelaySeconds: 50
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
    image: registry.camunda.cloud/cambpm-ee/camunda-bpm-platform-ee:${cambpmVersion}
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

void runRelease(params) {
  def pushChanges = 'true'
  def skipDeploy = 'false'

  if (!params.PUSH_CHANGES) {
    pushChanges = 'false'
    skipDeploy = 'true'
  }

  withCredentials([usernamePassword(
    credentialsId: optimizeUtils.defaultCredentialsId(),
    usernameVariable: 'GITHUB_APP',
    passwordVariable: 'GITHUB_ACCESS_TOKEN',
  )]) {
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

    // Access Optimize repo using access token.
    sh 'git remote set-url origin https://$GITHUB_APP:$GITHUB_ACCESS_TOKEN@github.com/camunda/camunda-optimize.git'

    sh("""
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
    string(name: 'ADDITIONAL_DOCKER_TAG', defaultValue: '', description: '(Optional) Any additional tag that should be added to the docker image.')
    booleanParam(name: 'PUSH_CHANGES', defaultValue: false, description: 'Should the changes be pushed to remote locations.')
  }

  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
  }

  options {
    skipDefaultCheckout(true)
    buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '3'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Prepare') {
      steps {
        optimizeCloneGitRepo(params.BRANCH)
        setBuildEnvVars()

        container('maven') {
          sh("""
            # install git
            apt-get update && \
            apt-get -y install git

            # git config
            git config --global user.email "ci_automation@camunda.com"
            git config --global user.name "${optimizeUtils.defaultCredentialsId()}"
            git config --global --add safe.directory "\$PWD"
          """)
        }
      }
    }
    stage('Release') {
      environment {
        PREVIOUS_VERSION = "${calculatePreviousVersion(params.RELEASE_VERSION)}"
      }
      steps {
        container('maven') {
          runRelease(params)
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
        DOCKER_LATEST = "${params.DOCKER_LATEST}"
        if(params.containsKey("ADDITIONAL_DOCKER_TAG")) {
          ADDITIONAL_DOCKER_TAG = "${params.ADDITIONAL_DOCKER_TAG}"
        } else {
          ADDITIONAL_DOCKER_TAG = ""
        }
        DOCKERHUB_REGISTRY_CREDENTIALS = credentials('camunda-dockerhub')
        REGISTRY_CAMUNDA_CLOUD = credentials('registry-camunda-cloud')
        MAJOR_OR_MINOR = isMajorOrMinorRelease(params.RELEASE_VERSION)
        // retrieve the git commit hash from the checked out tag of the release
        REVISION = sh(returnStdout: true, script: "git --git-dir target/checkout/.git log -n 1 --pretty=format:'%h'").trim()
        DATE = java.time.Instant.now().toString()
      }
      steps {
        container('docker') {
          sh("""#!/bin/bash -eux
          echo '${REGISTRY_CAMUNDA_CLOUD}' | docker login -u ci-optimize ${DOCKER_REGISTRY(params.PUSH_CHANGES)} --password-stdin

          tags=('${DOCKER_REGISTRY_IMAGE(params.PUSH_CHANGES)}:${VERSION}')
            
          docker_args=""
          if [ "${PUSH_CHANGES}" = true ]; then
            docker_args+="--push"

            docker login --username ${DOCKERHUB_REGISTRY_CREDENTIALS_USR} --password ${DOCKERHUB_REGISTRY_CREDENTIALS_PSW}
              
            tags+=('${DOCKERHUB_IMAGE()}:${VERSION}')
            # major and minor versions are always tagged as latest
            if [ "${MAJOR_OR_MINOR}" = true ] || [ "${DOCKER_LATEST}" = true ]; then
               tags+=('${DOCKER_REGISTRY_IMAGE(params.PUSH_CHANGES)}:latest')
               tags+=('${DOCKERHUB_IMAGE()}:latest')
            fi
            # an additional docker tag can optionally be provided
            if [ ! -z "${ADDITIONAL_DOCKER_TAG}" ]; then
               tags+=('${DOCKER_REGISTRY_IMAGE(params.PUSH_CHANGES)}:${ADDITIONAL_DOCKER_TAG}')
               tags+=('${DOCKERHUB_IMAGE()}:${ADDITIONAL_DOCKER_TAG}')
            fi
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
            --build-arg ARTIFACT_PATH=target/checkout/distro/target \
            --build-arg VERSION=${VERSION} \
            --build-arg DATE=${DATE} \
            --build-arg REVISION=${REVISION} \
            --platform linux/amd64,linux/arm64 \
            \${docker_args} \
            .
          """)
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
      sendEmailNotification()
    }
    always {
      retriggerBuildIfDisconnected()
    }
  }
}
