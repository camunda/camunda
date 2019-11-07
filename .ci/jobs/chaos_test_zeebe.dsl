// vim: set filetype=groovy:

pipelineJob('chaos-test') {
    displayName 'Zeebe Chaos Test'
    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/chaos_test_zeebe.groovy'))
            sandbox()
        }
    }
}
