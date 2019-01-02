#!/usr/bin/env groovy

// https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started

String storeNumOfBuilds() {
  return env.BRANCH_NAME == 'master' ? '30' : '10'
}

String storeNumOfArtifacts() {
  return env.BRANCH_NAME == 'master' ? '5' : '1'
}

def static PROJECT_DOCKER_IMAGE() { return "gcr.io/ci-30-162810/camunda-optimize" }

/************************ START OF PIPELINE ***********************/

String camBpmPodspec(String camBpmDockerImage, boolean secured = false) {
    String footer = (secured) ? """
      - name: ELASTIC_PASSWORD
        value: optimize
      - name: xpack.ssl.certificate_authorities
        value: /usr/share/elasticsearch/config/certs/ca/ca.crt
      - name: xpack.ssl.certificate
        value: /usr/share/elasticsearch/config/certs/optimize/optimize.crt
      - name: xpack.ssl.key
        value: /usr/share/elasticsearch/config/certs/optimize/optimize.key
      - name: xpack.security.transport.ssl.verification_mode
        value: certificate
      - name: xpack.security.transport.ssl.enabled
        value: true
      - name: xpack.security.http.ssl.enabled
        value: true
    volumeMounts:
    - name: es-config
      mountPath: /usr/share/elasticsearch/config/certs/ca/ca.crt
      subPath: ca.crt
    - name: es-config
      mountPath: /usr/share/elasticsearch/config/certs/ca/ca.key
      subPath: ca.key
    - name: es-config
      mountPath: /usr/share/elasticsearch/config/certs/optimize/optimize.crt
      subPath: optimize.crt
    - name: es-config
      mountPath: /usr/share/elasticsearch/config/certs/optimize/optimize.key
      subPath: optimize.key
    """ : ""
    return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  volumes:
  - name: cambpm-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-cambpm-config
  - name: es-config
    configMap:
      # Defined in: https://github.com/camunda-ci/k8s-infrastructure/tree/master/infrastructure/ci-30-162810/deployments/optimize
      name: ci-optimize-es-config
  imagePullSecrets:
  - name: registry-camunda-cloud-secret
  initContainers:
    - name: init-sysctl
      image: busybox
      imagePullPolicy: Always
      command: ["sysctl", "-w", "vm.max_map_count=262144"]
      securityContext:
        privileged: true
  containers:
  - name: maven
    image: maven:3.5.3-jdk-8-slim
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      # every JVM process will get a 1/2 of HEAP from total memory
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=\$(LIMITS_CPU)
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 3
        memory: 3Gi
      requests:
        cpu: 3
        memory: 3Gi
  - name: cambpm
    image: ${camBpmDockerImage}
    tty: true
    env:
      - name: JAVA_TOOL_OPTIONS
        value: |
          -XX:+UnlockExperimentalVMOptions
          -XX:+UseCGroupMemoryLimitForHeap
          -XX:MaxRAMFraction=1
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
    volumeMounts:
    - name: cambpm-config
      mountPath: /camunda/conf/bpm-platform.xml
      subPath: bpm-platform.xml
    - name: cambpm-config
      mountPath: /camunda/conf/tomcat-users.xml
      subPath: tomcat-users.xml
    - name: cambpm-config
      mountPath: /camunda/webapps/manager/META-INF/context.xml
      subPath: context.xml
  - name: elasticsearch
    image: docker.elastic.co/elasticsearch/elasticsearch:6.0.0
    securityContext:
      privileged: true
      capabilities:
        add: ["IPC_LOCK"]
    resources:
      requests:
        cpu: 1
        memory: 1Gi
    ports:
      - containerPort: 9200
        name: http
        protocol: TCP
      - containerPort: 9300
        name: transport
        protocol: TCP
    env:
      - name: ES_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
      - name: ES_JAVA_OPTS
        value: "-Xms512m -Xmx512m"
      - name: cluster.name
        value: elasticsearch
    """ + footer
}

pipeline {
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yamlFile '.ci/podSpecs/builderAgent.yml'
    }
  }

  // Environment
  environment {
    NODE_ENV = "ci"
    NEXUS = credentials("camunda-nexus")
    GCR_REGISTRY = credentials('docker-registry-ci3')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: storeNumOfBuilds(), artifactNumToKeepStr: storeNumOfArtifacts()))
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  stages {
    stage('Build') {
      steps {
        container('maven') {
          runMaven('install -Pproduction -Dskip.docker -DskipTests -T\$LIMITS_CPU')
          // Loops with symbolic links cause stashing to fail
          sh ('rm -fr client/node_modules')
        }
        stash name: "optimize-stash"
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
              junit testResults: '**/surefire-reports/**/*.xml', keepLongStdio: true
            }
          }
        }
        stage('Frontend') {
          steps {
            container('node') {
              sh('''
                cd ./client
                yarn
                yarn test:ci
              ''')
            }
          }
          post {
            always {
              junit testResults: 'client/jest-test-results.xml', keepLongStdio: true, allowEmptyResults: true
            }
          }
        }
      }
    }
    stage('Integration and Migration tests') {
      environment {
        CAM_REGISTRY = credentials('repository-camunda-cloud')
      }
      failFast true
      parallel {
        stage('Migration') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-migration_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml camBpmPodspec('camunda/camunda-bpm-platform:7.10.0')
            }
          }
          steps {
            migrationTestSteps('latest')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveTestArtifacts('backend', 'migration')
            }
          }
        }
        stage('Data upgrade test') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-data-upgrade_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml camBpmPodspec('camunda/camunda-bpm-platform:7.10.0')
            }
          }
          steps {
            dataUpgradeTestSteps()
          }
        }
        stage('Security') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-security_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml camBpmPodspec('camunda/camunda-bpm-platform:7.11.0-SNAPSHOT', true)
            }
          }
          steps {
            securityTestSteps()
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
          }
        }
        stage('IT Latest') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-latest_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml camBpmPodspec('camunda/camunda-bpm-platform:7.11.0-SNAPSHOT')
            }
          }
          steps {
            unstash name: "optimize-stash"
            integrationTestSteps('latest')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveArtifacts( artifacts: "latest/**/*" )
            }
          }
        }
        stage('IT 7.9') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.9_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml camBpmPodspec('registry.camunda.cloud/camunda-bpm-platform-ee:7.9.7')
            }
          }
          steps {
            unstash name: "optimize-stash"
            integrationTestSteps('7.9')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveTestArtifacts('backend', '7_9')
            }
          }
        }
        stage('IT 7.8') {
          agent {
            kubernetes {
              cloud 'optimize-ci'
              label "optimize-ci-build-it-7.8_${env.JOB_BASE_NAME}-${env.BUILD_ID}"
              defaultContainer 'jnlp'
              yaml camBpmPodspec('registry.camunda.cloud/camunda-bpm-platform-ee:7.8.13')
            }
          }
          steps {
            unstash name: "optimize-stash"
            integrationTestSteps('7.8')
          }
          post {
            always {
              junit testResults: 'backend/target/failsafe-reports/**/*.xml', allowEmptyResults: true, keepLongStdio: true
            }
            failure {
              archiveTestArtifacts('backend', '7_8')
            }
          }
        }
      }
    }
    stage('RESTAPI Docs') {
      steps {
        container('maven') {
          runMaven('clean package -Pdocs,production -DskipTests -f backend/pom.xml')
        }
      }
      post {
        success {
          archiveArtifacts artifacts: 'backend/target/docs/**/*.*'
        }
      }
    }
    stage('Deploy') {
      parallel {
        stage('Deploy to Nexus') {
          when {
            branch 'master'
          }
          steps {
            container('maven') {
              runMaven('deploy -Pproduction -Dskip.fe.build -DskipTests')
            }
          }
        }
        stage('Build Docker') {
          when {
            expression { BRANCH_NAME ==~ /(master|.*-deploy)/ }
          }
          environment {
            VERSION = readMavenPom().getVersion().replace('-SNAPSHOT', '')
            SNAPSHOT = readMavenPom().getVersion().contains('SNAPSHOT')
            IMAGE_TAG = getImageTag()
            GCR_REGISTRY = credentials('docker-registry-ci3')
          }
          steps {
            container('docker') {
              sh ("""
                echo '${GCR_REGISTRY}' | docker login -u _json_key https://gcr.io --password-stdin

                docker build -t ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG} \
                  --build-arg=SKIP_DOWNLOAD=true \
                  --build-arg=VERSION=${VERSION} \
                  --build-arg=SNAPSHOT=${SNAPSHOT} \
                  --build-arg=USERNAME=${NEXUS_USR} \
                  --build-arg=PASSWORD=${NEXUS_PSW} \
                  .

                docker push ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG}

                if [ "${env.BRANCH_NAME}" = 'master' ]; then
                  docker tag ${PROJECT_DOCKER_IMAGE()}:${IMAGE_TAG} ${PROJECT_DOCKER_IMAGE()}:latest
                  docker push ${PROJECT_DOCKER_IMAGE()}:latest
                fi
              """)
            }
          }
        }
      }
    }
    stage ('Deploy to K8s') {
      when {
        expression { BRANCH_NAME ==~ /(.*-deploy)/ }
      }
      steps {
        build job: '/deploy-optimize-branch-to-k8s',
          parameters: [
              string(name: 'BRANCH', value: getBranchSlug()),
          ]
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
  }
}

/************************ END OF PIPELINE ***********************/

String getGitCommitHash() {
  return sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
}

String getBranchSlug() {
  return env.BRANCH_NAME.toLowerCase().replaceAll(/[^a-z0-9-]/, '-').minus('-deploy')
}

String getImageTag() {
  return env.BRANCH_NAME == 'master' ? getGitCommitHash() : "branch-${getBranchSlug()}"
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

void integrationTestSteps(String engineVersion = 'latest') {
  container('maven') {
    runMaven("install -Dskip.docker -Dskip.fe.build -Pproduction,it,engine-${engineVersion} -pl backend -am -T\$LIMITS_CPU")
  }
}

void securityTestSteps() {
  container('maven') {

    // build all required artifacts for security tests
    runMaven("install -Dskip.docker -DskipTests -Pproduction,it -pl backend,qa/connect-to-secured-es-tests -am -T\$LIMITS_CPU")
    // run migration tests
    runMaven("verify -Dskip.docker -f qa/connect-to-secured-es-tests/pom.xml -Psecured-es-it")
  }
}

void migrationTestSteps(String engineVersion = 'latest') {
  container('maven') {
    sh ("""apt-get update && apt-get install -y jq""")

    // build all required artifacts for migration tests
    runMaven("install -Dskip.docker -DskipTests -Pproduction,it,engine-${engineVersion} -pl backend,upgrade -am -T\$LIMITS_CPU")
    // run migration tests
    runMaven("verify -Dskip.docker -f qa/upgrade-es-schema-tests/pom.xml -Pupgrade-es-schema-tests")
  }
}

void dataUpgradeTestSteps(String engineVersion = 'latest') {
  container('maven') {
    sh ("""apt-get update && apt-get install -y jq""")

    runMaven("install -Dskip.docker -DskipTests -Pproduction,it,engine-${engineVersion} -pl backend,upgrade,distro -am -T\$LIMITS_CPU")

    runMaven("verify -Dskip.docker -f qa/upgrade-optimize-data/pom.xml -Pupgrade-optimize-data")
  }
}

void dockerRegistryLogin() {
  sh ("""echo '${CAM_REGISTRY_PSW}' | docker login -u ${CAM_REGISTRY_USR} registry.camunda.cloud --password-stdin""")
}

void archiveTestArtifacts(String srcDirectory, String destDirectory = null) {
  container('maven') {
    // fix permissions for jnlp slave as maven user is root so we can archive the artifacts
    sh ("""#!/bin/bash -ex
      chown -R 10000:10000 ${srcDirectory}/target/{es_logs,cambpm_logs}
    """)
  }

  if (destDirectory == null) {
    destDirectory = srcDirectory
  } else {
    sh ("""
      mkdir -p ${destDirectory}
      cp -R ${srcDirectory}/target/es_logs ${srcDirectory}/target/cambpm_logs ${destDirectory}
    """)
  }

  archiveArtifacts(
    artifacts: "${destDirectory}/**/*",
    allowEmptyResults: true,
    onlyIfSuccessful: false
  )
}

void runMaven(String cmd) {
  sh ("mvn ${cmd} -s settings.xml -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
}
