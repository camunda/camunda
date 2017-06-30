def gitBranch = 'master'
def jobName = 'zb-dispatcher-benchmarks'

def buildScript =
"""\
#!/bin/bash -xe

mvn clean package -B -DskipTests
java -Dburst.size=100 -jar benchmarks/target/benchmarks.jar -bm sample -tu ns
"""

job(jobName)
{
    scm
    {
        git
        {
            remote
            {
                github 'camunda-tngp/zb-dispatcher', 'ssh'
                credentials 'camunda-jenkins-github-ssh'
            }
            branch gitBranch
            extensions
            {
                localBranch gitBranch
            }
        }
    }
    label 'cam-int-1'
    jdk '(Default)'

    steps
    {
        shell buildScript
    }

    wrappers
    {
        timestamps()

        timeout
        {
            absolute 60
        }

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
