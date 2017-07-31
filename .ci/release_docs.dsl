// vim: set filetype=groovy:

def jobName = 'zeebe-docs'
def repository = 'zeebe'
def gitBranch = 'master'

freeStyleJob(jobName)
{
    scm
    {
        git
        {
            remote
            {
                github 'camunda-zeebe/' + repository, 'ssh'
                credentials 'camunda-jenkins-github-ssh'
            }
            branch gitBranch
            extensions
            {
                localBranch gitBranch
                pathRestriction {
                    includedRegions 'docs/.*'
                    excludedRegions ''
                }
            }
        }
    }
    triggers
    {
        githubPush()
    }

    label 'master'

    steps
    {
        shell readFileFromWorkspace('.ci/release_docs.sh')
    }

    wrappers
    {
        timestamps()

        timeout
        {
            absolute 10
        }

        sshAgent 'docs-zeebe-io-ssh'

    }

    publishers
    {
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

    logRotator(-1, 5, -1, 1)

}
