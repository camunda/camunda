// vim: set filetype=groovy:

pipelineJob('chaos-test') {
    displayName 'Zeebe Chaos Test'
    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/chaos_test_zeebe.groovy'))
            sandbox()
        }
    }

    properties {
        logRotator {
            numToKeep(10)
            daysToKeep(-1)
            artifactDaysToKeep(-1)
            artifactNumToKeep(10)
        }

        pipelineTriggers {
            triggers {
                cron {
                    spec('H 1 * * *')
                }
            }
        }
    }
}
