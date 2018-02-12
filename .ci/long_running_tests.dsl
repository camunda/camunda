// vim: set filetype=groovy:

def gitBranch = 'master'
def jobName = 'zeebe-long-running-tests'
def repository = 'zeebe'

def pom = 'qa/long-running-tests/pom.xml'
def mvnGoals = 'clean test -P long-running-tests -B'

def mavenVersion = 'maven-3.3-latest'
def mavenSettingsId = 'camunda-maven-settings'

mavenJob(jobName)
{
    disabled()

    scm
    {
        git
        {
            remote
            {
                github 'zeebe-io/' + repository, 'ssh'
                credentials 'camunda-jenkins-github-ssh'
            }
            branch gitBranch
            extensions
            {
                localBranch gitBranch
            }
        }
    }

    triggers
    {
       cron('@midnight')
    }

    label 'cam-int-3'
    jdk 'jdk-8-latest'

    rootPOM pom
    goals mvnGoals
    localRepository LocalRepositoryLocation.LOCAL_TO_WORKSPACE
    providedSettings mavenSettingsId
    mavenInstallation mavenVersion

    preBuildSteps {
        // remove old zeebe data to ensure enough disk space is available
        shell 'rm -rf /tmp/zeebe-data-*'
    }

    wrappers
    {
        timestamps()

        timeout
        {
            absolute(12 * 60) // 12h
        }

        configFiles
        {
            // nexus settings xml
            mavenSettings(mavenSettingsId)
            {
                variable('NEXUS_SETTINGS')
            }
        }

    }

    publishers
    {

        archiveJunit('**/target/surefire-reports/*.xml')
        {
            retainLongStdout()
        }

        extendedEmail
        {
            triggers
            {
                firstFailure
                {
                    sendTo
                    {
                       culprits()
                    }
                }
                fixed
                {
                    sendTo
                    {
                        culprits()
                    }
                }
            }
        }
    }

    blockOnUpstreamProjects()
    logRotator(-1, 3, -1, 3)
    compressBuildLog()

}
