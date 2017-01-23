def githubOrga = 'camunda'
def repository = 'camunda-optimize'
def gitBranch = 'master'

job('snapshot-docs') {
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
       goals('-DskipTests -Pdocs clean package')
       mavenInstallation('maven-3.3-latest')
       rootPOM('optimize-backend/pom.xml')
    }

    publishers {
      archiveArtifacts {
        pattern('optimize-backend/target/docs/**/*.*')
        onlyIfSuccessful()
      }
    }
  }
}