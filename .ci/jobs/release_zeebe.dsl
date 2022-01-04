// vim: set filetype=groovy:

pipelineJob('zeebe-release') {
    displayName 'Zeebe Release'
    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/release_zeebe.groovy'))
            sandbox()
        }
    }

    parameters {
        stringParam('RELEASE_VERSION', '0.X.0', 'Which version to release?')
        stringParam('DEVELOPMENT_VERSION', '0.Y.0-SNAPSHOT', 'Next development version?')
        booleanParam('PUSH_CHANGES', true, 'Push release to remote repositories and github?')
        booleanParam('PUSH_DOCKER', true, 'Push release to docker hub?')
        booleanParam('IS_LATEST', true, 'Should the docker image be tagged as camunda/zeebe:latest?')
    }
}
