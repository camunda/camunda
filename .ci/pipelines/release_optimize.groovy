#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

// general properties for CI execution
static String NODE_POOL() { return "slaves-stable" }
static String MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }
static String DIND_DOCKER_IMAGE() { return "docker:18.06-dind" }

static String PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }

String calculatePreviousVersion(releaseVersion) {
  def version = releaseVersion.tokenize('.')
  def majorVersion = version[0]
  def minorVersion = version[1]
  def patchVersion = version[2]

  if (!patchVersion.contains('-') && patchVersion == '0') {
    // only major / minor GA (.0) release versions will trigger an auto-update of previousVersion property.
    println 'Auto-updating previousVersion property as release version is a valid major/minor version.'
    return releaseVersion
  } else {
    println 'Not auto-updating previousVersion property as release version is not a valid major/minor version.'
    return ""
  }
}

static String mavenDindAgent(env) {
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
        cpu: 4
        memory: 3Gi
      requests:
        cpu: 2
        memory: 3Gi
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
        cpu: 1
        memory: 1Gi
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

  sh ("""
    mvn -DpushChanges=${pushChanges} -DskipTests -Prelease,production,engine-latest release:prepare release:perform \
    -Dtag=${params.RELEASE_VERSION} -DreleaseVersion=${params.RELEASE_VERSION} -DdevelopmentVersion=${params.DEVELOPMENT_VERSION} \
    --settings=settings.xml '-Darguments=--settings=settings.xml -DskipTests -DskipNexusStagingDeployMojo=${skipDeploy} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn' \
    -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
  """)


  sh ("""
    # This is needed to not abort the job in case 'git diff' returns a status different from 0
    set +e
    # auto-update previousVersion property
    if [ ! -z \"\${PREVIOUS_VERSION}\" ]; then
      sed -i "s/project.previousVersion>.*</project.previousVersion>${params.RELEASE_VERSION}</g" pom.xml
      git add pom.xml
      
      cd qa/upgrade-optimize-data/post-migration-test/ 

      sed -i "s/,\\\${project.version}<\\/upgrade.versions>/<\\/upgrade.versions>/g" pom.xml
      sed -i "s/<\\/upgrade.versions>/,${params.RELEASE_VERSION}<\\/upgrade.versions>/g" pom.xml
      sed -i "s/<\\/upgrade.versions>/,\\\${project.version}<\\/upgrade.versions>/g" pom.xml
      git add pom.xml
            
      cd ../../../
      
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
      yaml mavenDindAgent(env)
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '3.0.0', description: 'Version to release. Applied to pom.xml and Git tag.')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '3.1.0-SNAPSHOT', description: 'Next development version.')
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

        container('maven') {
          sh ('''
            # install git and ssh
            apt-get update && \
            apt-get -y install git openssh-client

            # setup ssh for github
            mkdir -p ~/.ssh
            ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts

            # git config
            git config --global user.email "ci@camunda.com"
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
    stage('Upload camunda.org') {
      when {
        expression { params.PUSH_CHANGES == true }
      }
      steps {
        container('jnlp') {
          sshagent(['jenkins-camunda-web']) {
            sh ("""#!/bin/bash -xe
              ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no jenkins_camunda_web@vm29.camunda.com "mkdir -p /var/www/camunda/camunda.org/enterprise-release/optimize/${params.RELEASE_VERSION}/"
              for file in target/checkout/distro/target/*.{tar.gz,zip}; do
                scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \${file} jenkins_camunda_web@vm29.camunda.com:/var/www/camunda/camunda.org/enterprise-release/optimize/${params.RELEASE_VERSION}/
              done
            """)
          }
        }
      }
    }
    stage('Docker Image') {
      when {
        expression { params.PUSH_CHANGES == true }
      }
      environment {
        VERSION = "${params.RELEASE_VERSION}"
        GCR_REGISTRY = credentials('docker-registry-ci3')
      }
      steps {
        container('docker') {
          sh ("""
            echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin

            docker build -t ${PROJECT_DOCKER_IMAGE()}:${VERSION} \
              --build-arg SKIP_DOWNLOAD=true \
              --build-arg VERSION=${VERSION} \
              --build-arg SNAPSHOT=false \
              --build-arg USERNAME="${NEXUS_USR}" \
              --build-arg PASSWORD="${NEXUS_PSW}" \
              .

            docker push ${PROJECT_DOCKER_IMAGE()}:${VERSION}
          """)
        }
      }
    }
    stage('Trigger release example repo job') {
      when {
        expression { params.PUSH_CHANGES == true }
      }
      steps {
        build job: '/camunda-optimize-example-repo-release',
            parameters: [
                string(name: 'RELEASE_VERSION', value: "${params.RELEASE_VERSION}"),
                string(name: 'DEVELOPMENT_VERSION', value: "${params.DEVELOPMENT_VERSION}"),
                string(name: 'BRANCH', value: "${params.BRANCH}"),
            ],
        wait: false
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
  }
}
