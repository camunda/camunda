// vim: set filetype=groovy:

pipelineJob('zeebe-docker') {
    displayName 'camunda/zeebe Docker Image'
    definition {
        cps {
            script(readFileFromWorkspace('.ci/pipelines/docker_zeebe.groovy'))
            sandbox()
        }
    }

    parameters {
        stringParam('BRANCH', 'develop', 'Which zeebe-io/zeebe branch to build and push?')
        stringParam('VERSION', '', 'Zeebe version to build the image for')
        booleanParam('IS_LATEST', false, 'Should the docker image be tagged as camunda/zeebe:latest?')
    }
}
