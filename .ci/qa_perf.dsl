// vim: set filetype=groovy:

def gitBranch = 'master'
def commonName = 'camunda-tngp-QA-performance'
def triggerJobName = commonName + '-tests-trigger'


// map of tests to execute and the corresponding make targets
def TESTS = [
    [
        name: 'create-task-throughput',
        target: 'test-create-task-throughput'
    ],
    [
        name: 'create-task-throughput-idle-subscription',
        target: 'test-create-task-throughput-idle-subscription'
    ],
    [
        name: 'create-task-latency',
        target: 'test-create-task-latency'
    ],
    [
        name: 'start-wf-instance-throughput',
        target: 'test-start-wf-instance-throughput'
    ],
    [
        name: 'start-wf-instance-latency',
        target: 'test-start-wf-instance-latency'
    ],
    [
        name: 'task-subscription-throughput',
        target: 'test-task-subscription-throughput'
    ],
    [
        name: 'topic-subscription-throughput',
        target: 'test-topic-subscription-throughput'
    ]
]

// generate job names from tests
def JOBS = TESTS.collectEntries
{ test ->
    [("${commonName}-${test.name}"): test.target]
}


// job which triggers all perf jobs
// can be triggered manually and is also used as downstream of the distro job
job(triggerJobName)
{
    label 'master'
    logRotator(-1, 50, -1, 50)

    publishers
    {
        downstream(JOBS.keySet().join(','), 'SUCCESS')
    }

}


// performance tests jobs
JOBS.each
{ jobName, makeTarget ->

    job(jobName)
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
            shell makeStep(makeTarget);
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
        logRotator(-1, 50, -1, 50)

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

// create build script for given make target
static String makeStep(String target) {
    return """\
#!/bin/bash -xe

cd qa/perf-tests/
export BROKER_HOST=192.168.0.21
make -e clean build
make -e deploy-broker
make -e ${target}
make -e clean
"""
}
