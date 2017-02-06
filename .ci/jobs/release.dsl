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
    shell ('''\
      cd client
      yarn
      yarn run compile
    '''.stripIndent())
    shell ("""\
      mvn -DskipTests -Prelease,production release:prepare release:perform \
      -Dtag=\${RELEASE_VERSION} -DreleaseVersion=\${RELEASE_VERSION} -DdevelopmentVersion=\${DEVELOPMENT_VERSION} \
      --settings=\${MAVEN_NEXUS_SETTINGS} \"-Darguments=--settings=\${MAVEN_NEXUS_SETTINGS} -DskipTests\" -B
    """.stripIndent())
  }

  wrappers {
    timestamps()

    timeout {
      absolute 15
    }

    sshAgent 'camunda-jenkins-github-ssh'

    configFiles {
      mavenSettings('camunda-maven-settings') {
        variable('MAVEN_NEXUS_SETTINGS')
      }
    }
  }

  blockOnUpstreamProjects()
  logRotator(-1, 5, -1, 1)

}
