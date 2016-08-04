def jobName = 'util-DISTRO-maven-deploy'
def repository = 'util'
def gitBranch = 'master'

def pom = 'pom.xml'
def mvnGoals = 'verify'

mavenJob(jobName) {
  scm {
    git {
      remote {
        github 'camunda-tngp/' + repository, 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        cloneOptions {
          shallow()
        }
        localBranch gitBranch
      }
    }
  }
  triggers {
    scm 'H/5 * * * *'
    // only works when manually setting up the webhooks for repository in GitHub
    // githubPush()
  }
  label 'ubuntu'
  jdk 'jdk-8-latest'

  rootPOM pom
  goals mvnGoals
  localRepository LocalRepositoryLocation.LOCAL_TO_WORKSPACE
//      mavenOpts  # set mvn java opts like XMX etc.
  providedSettings 'camunda-maven-settings'
  mavenInstallation 'maven-3.3-latest'

  wrappers {
    timestamps()
    timeout {
      absolute 60
    }
  }

  publishers {
    deployArtifacts {
      repositoryId 'camunda-nexus'
      repositoryUrl 'https://app.camunda.com/nexus/content/repositories/camunda-tngp-snapshots'
      uniqueVersion true
      evenIfUnstable false
    }
    archiveJunit '**/target/surefire-reports/*.xml'

  // downstream('project-a', 'SUCCESS') # trigger downstream projects by name if required
  }

  blockOnUpstreamProjects()
  logRotator(-1, 5, -1, 1)

}