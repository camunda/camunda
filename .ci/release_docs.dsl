// vim: set filetype=groovy:

def jobName = 'zeebe-docs'
def repository = 'zeebe'

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
            branch '$RELEASE_VERSION'
        }
    }

    parameters
    {
        stringParam('RELEASE_VERSION', 'master', 'Git commit/tag to publish')
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
