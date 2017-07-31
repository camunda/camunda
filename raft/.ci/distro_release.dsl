def jobName = 'zb-raft-DISTRO-maven-deploy'
def repository = 'zb-raft'
def gitBranch = 'master'

def pom = 'pom.xml'
def mvnGoals = 'clean license:check verify'

def mavenVersion = 'maven-3.3-latest'
def mavenSettings = 'camunda-maven-settings'

// script to set access rights on ssh keys
// and configure git user name and email
def setupGitConfig = '''\
#!/bin/bash -xe

chmod 600 ~/.ssh/id_rsa
chmod 600 ~/.ssh/id_rsa.pub

git config --global user.email "ci@camunda.com"
git config --global user.name "camunda-jenkins"
'''

// properties used by the release build
def releaseProperties = [
    resume: 'false',
    tag: '${RELEASE_VERSION}',
    releaseVersion: '${RELEASE_VERSION}',
    developmentVersion: '${DEVELOPMENT_VERSION}',
    arguments: '--settings=${NEXUS_SETTINGS} -Dskip.central.release=true'
]


mavenJob(jobName) {
  scm {
    git {
      remote {
        github 'camunda-zeebe/' + repository, 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        localBranch gitBranch
      }
    }
  }
  triggers {
    githubPush()
  }
  label 'ubuntu'
  jdk 'jdk-8-latest'

  rootPOM pom
  goals mvnGoals
  localRepository LocalRepositoryLocation.LOCAL_TO_WORKSPACE
  providedSettings mavenSettings
  mavenInstallation mavenVersion

  wrappers {
    timestamps()

    timeout {
      absolute 60
    }

    configFiles {
        // jenkins github public ssh key needed to push to github
        custom('Jenkins CI GitHub SSH Public Key') {
            targetLocation '/home/camunda/.ssh/id_rsa.pub'
        }
        // jenkins github private ssh key needed to push to github
        custom('Jenkins CI GitHub SSH Private Key') {
            targetLocation '/home/camunda/.ssh/id_rsa'
        }
    }

    release {
      doNotKeepLog false
      overrideBuildParameters true

      parameters {
        stringParam('RELEASE_VERSION', '0.1.0', 'Version to release')
        stringParam('DEVELOPMENT_VERSION', '0.2.0-SNAPSHOT', 'Next development version')
      }

      preBuildSteps {
        // setup git configuration to push to github
        shell setupGitConfig

        // execute maven release
        maven {
          mavenInstallation mavenVersion
          providedSettings mavenSettings
          goals 'release:prepare release:perform -B'
          properties releaseProperties
          localRepository LocalRepositoryLocation.LOCAL_TO_WORKSPACE
        }
      }
    }

  }

  publishers {
    deployArtifacts {
      repositoryId 'camunda-nexus'
      repositoryUrl 'https://app.camunda.com/nexus/content/repositories/camunda-zeebe-snapshots'
      uniqueVersion true
      evenIfUnstable false
    }

    archiveJunit '**/target/surefire-reports/*.xml'

    extendedEmail {
      triggers {
        firstFailure {
          sendTo {
            culprits()
          }
        }
        fixed {
          sendTo {
            culprits()
          }
        }
      }
    }
  }

  blockOnUpstreamProjects()
  logRotator(-1, 5, -1, 1)

}
