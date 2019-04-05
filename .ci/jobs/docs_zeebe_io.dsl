// vim: set filetype=groovy:

pipelineJob('zeebe-docs') {
    displayName 'zeebe.io Documentation'
    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/docs_zeebe_io.groovy'))
            sandbox()
        }
    }

    parameters {
        stringParam('BRANCH', 'develop', 'Which zeebe-io/zeebe branch to build and push?')
        booleanParam('LIVE', false, 'Deploy to https://docs.zeebe.io intead of https://stage.docs.zeebe.io?')
    }
}
