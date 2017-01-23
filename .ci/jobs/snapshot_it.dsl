def githubOrga = 'camunda'
def repository = 'camunda-optimize'
def gitBranch = 'master'

job('snapshot-it') {
  scm {
    git {
      remote {
        github "${githubOrga}/${repository}", 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        localBranch gitBranch
      }
    }
  }

  triggers {
    scm 'H/5 * * * *'
  }

  label 'ubuntu'
  jdk 'jdk-8-latest'

  steps {
    maven {
       rootPOM('optimize-backend/pom.xml')
       goals('clean verify')
       mavenInstallation('maven-3.3-latest')
    }
  }
}