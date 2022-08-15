pipelineJob('generate-cambpm-test-data') {

    displayName 'Generate Camunda BPM test datasets'
    description 'Generates and stores Camunda BPM test datasets for use in other jobs'

    // By default, this job is disabled in non-prod envs.
    if (ENVIRONMENT != "prod") {
        disabled()
    }

    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/generate_cambpm_test_data.groovy'))
            sandbox()
        }
    }

    parameters {
        stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for Camunda BPM test datasets generation.')
        stringParam('POSTGRES_VERSION', '11.2', 'Postgres version to use.')
        stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
        choiceParam('SQL_DUMP', ['optimize_data-large.sqlc', 'optimize_data-medium.sqlc', 'optimize_data-stage.sqlc', 'optimize_data-e2e.sqlc', 'optimize_data-query-performance.sqlc'])
        booleanParam('USE_E2E_PRESETS', false, 'When enabled loads E2E test dataset presets and overwrites all of the below.')
        booleanParam('USE_QUERY_PERFORMANCE_PRESETS', false, 'When enabled loads query performance dataset presets and overwrites all of the below.')
        stringParam('NUM_PROCESS_INSTANCES', '10000000', 'Number of process instances to generate.')
        booleanParam('ADJUST_PROCESS_INSTANCE_DATES', false, 'Flag to indicate whether process instance dates should be adjusted. Should be true for stage data and false for larger data sets.')
        stringParam('PROCESS_DEFINITIONS', '', 'Optional, comma-separated list of definitions and number of versions to generate data for, e.g. "InvoiceWithAlternativeCorrelationVariable:5,InvoiceDataFor2Tenants:2".')
        stringParam('NUM_DECISION_INSTANCES', '100000', 'At least the number of decision instances stated here will be generated but due to the DRD structure about 1/3 more than that.')
        stringParam('DECISION_DEFINITIONS', '', 'Optional, comma-separated list of definitions and number of versions to generate data for, e.g. "DecideDish:5,InvoiceBusinessDecisions:5".')
    }

}
