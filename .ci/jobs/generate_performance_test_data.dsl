/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
pipelineJob('generate-performance-test-data') {

    displayName 'Generate performance test dataset'
    description 'Generates and stores performance test data for use in other jobs'

    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/generate_performance_test_data.groovy'))
            sandbox()
        }
    }

    parameters {
        stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
        stringParam('POSTGRES_VERSION', '11.2', 'Postgres version to use.')
        stringParam('CAMBPM_VERSION', '7.11.1', 'Camunda BPM version to use.')
        choiceParam('SQL_DUMP', ['optimize_large_data-performance.sqlc', 'optimize_large_data-stage.sqlc'])
        stringParam('NUM_INSTANCES', '10000000', 'Number of process instances to generate')
    }

}
