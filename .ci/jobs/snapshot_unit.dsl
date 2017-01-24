def githubOrga = 'camunda'
def repository = 'camunda-optimize'
def gitBranch = 'master'

job('snapshot-unit') {
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

  label 'build'
  jdk '(Default)'

  wrappers {
    environmentVariables {
      env('DISPLAY', ':0')
    }
  }

  publishers {
    archiveJunit('**/surefire-reports/**/*.xml') {
      allowEmptyResults()
      retainLongStdout()
      healthScaleFactor(1.5)
      testDataPublishers {
        publishTestStabilityData()
      }
    }
  }

  steps {
    shell '''#!/bin/bash
cd client
yarn
'''
    maven {
       goals('clean package')
     }
  }
}
