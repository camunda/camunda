def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

freeStyleJob('camunda-optimize-release') {

  displayName 'Release Camunda Optimize'
  description 'Release Camunda Optimize to Camunda Nexus and tag GitHub repository.'

  parameters {
    stringParam('RELEASE_VERSION', '1.0.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '1.1.0-SNAPSHOT', 'Next development version.')
  }

  scm {
    git {
      remote {
        github "${githubOrga}/${gitRepository}", 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        cleanBeforeCheckout()
        localBranch gitBranch
      }
    }
  }

  label 'optimize-build'
  jdk 'jdk-8-latest'

  steps {
    shell ("""\
      mvn -DskipTests -Prelease,production release:prepare release:perform \
      -Dtag=\${RELEASE_VERSION} -DreleaseVersion=\${RELEASE_VERSION} -DdevelopmentVersion=\${DEVELOPMENT_VERSION} \
      --settings=\${MAVEN_NEXUS_SETTINGS} \"-Darguments=--settings=\${MAVEN_NEXUS_SETTINGS} -DskipTests\" -B
    """.stripIndent())
    shell ('''#!/bin/bash -xe
ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no jenkins_camunda_web@vm29.camunda.com "mkdir -p /var/www/camunda/camunda.org/enterprise-release/optimize/${RELEASE_VERSION}/"
for file in distro/target/*.{tar.gz,zip}; do
  scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${file} jenkins_camunda_web@vm29.camunda.com:/var/www/camunda/camunda.org/enterprise-release/optimize/${RELEASE_VERSION}/
done
    ''')
  }

  wrappers {
    timestamps()

    timeout {
      absolute 15
    }

    sshAgent('camunda-jenkins-github-ssh','jenkins_camunda_web')

    configFiles {
      mavenSettings('camunda-maven-settings') {
        variable('MAVEN_NEXUS_SETTINGS')
      }
    }
  }

  publishers {
    extendedEmail {
      recipientList('optimize@camunda.com')
      triggers {
        statusChanged {
          sendTo {
            requester()
          }
        }
      }
    }
  }

  blockOnUpstreamProjects()
  logRotator(-1, 5, -1, 1)

}
