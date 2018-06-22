// vim: set filetype=groovy:

def jobName = 'zeebe-docs-stage'
def repository = 'zeebe'
def gitBranch = 'develop'

freeStyleJob(jobName)
{
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
        githubPush()
    }

    label 'master'

    steps
    {
        shell readFileFromWorkspace('.ci/stage_docs.sh')
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
