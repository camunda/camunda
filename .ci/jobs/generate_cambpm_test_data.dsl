pipelineJob('generate-cambpm-test-data') {

    displayName 'Generate Camunda BPM test datasets'
    description 'Generates and stores Camunda BPM test datasets for use in other jobs'

    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/generate_cambpm_test_data.groovy'))
            sandbox()
        }
    }

    parameters {
        stringParam('BRANCH', 'master', 'Branch to use for Camunda BPM test datasets generation.')
        stringParam('POSTGRES_VERSION', '11.2', 'Postgres version to use.')
        stringParam('CAMBPM_VERSION', '7.11.1', 'Camunda BPM version to use.')
        choiceParam('SQL_DUMP', ['optimize_large_data-performance.sqlc', 'optimize_large_data-stage.sqlc'])
        stringParam('NUM_INSTANCES', '10000000', 'Number of process instances to generate.')
    }

}
