def gitBranch = 'master'
def commonName = 'camunda-tngp-QA-performance'
def jobPrefix = 'TEST-' + commonName
def triggerJobName = commonName + '-tests-trigger'

def buildScript =
"""\
#!/bin/bash -xe

cd qa/perf-tests/
export BROKER_HOST=192.168.0.21
make -e clean build
make -e deploy-broker
make -e @test@
make -e clean
"""

// name -> make target
def TESTS =
[
    'create-task-throughput': 'test-create-task-throughput',
    'create-task-latency': 'test-create-task-latency',
    'start-wf-instance-throughput': 'test-start-wf-instance-throughput',
    'start-wf-instance-latency': 'test-start-wf-instance-latency'
 ]


// common root job for perf tests
job(triggerJobName)
{
    label 'master'
    logRotator(-1, 5, -1, 1)

    publishers
    {
        downstream(TESTS.keySet().collect{ jobPrefix + it }.join(','), 'SUCCESS')
    }

}


// performance tests jobs
TESTS.each
{ testName, makeTarget ->

    job(jobPrefix + testName)
    {
        scm
        {
            git
            {
                remote
                {
                    github 'camunda-tngp/camunda-tngp', 'ssh'
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
            shell buildScript.replace('@test@', makeTarget)
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
            archiveArtifacts
            {
                pattern 'qa/perf-tests/data/**/*'
                allowEmpty false
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
        logRotator(-1, 5, -1, 1)

    }

}


// create corresponding view
listView('Performance-Tests')
{
    jobs
    {
        regex ".*${commonName}.*"
    }

    configure
    { view ->
        // columns
        view / columns <<
        {
            'hudson.views.StatusColumn' {}
            'hudson.views.WeatherColumn' {}
            'hudson.views.BuildButtonColumn' {}
            'hudson.plugins.release.ReleaseButtonColumn' {}
            'jenkins.plugins.extracolumns.LastBuildConsoleColumn' {}
            'jenkins.plugins.extracolumns.ConfigureProjectColumn' {}
            'hudson.views.JobColumn' {}
            'com.robestone.hudson.compactcolumns.LastSuccessAndFailedColumn'
            {
              timeAgoTypeString 'DIFF'
            }
            'jenkins.plugins.extracolumns.BuildDurationColumn'
            {
              buildDurationType 1
            }
        }
    }
}
