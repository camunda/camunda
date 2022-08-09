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
        stringParam('BRANCH', 'main', 'Which branch to build and push?')
        stringParam('VERSION', '', 'Zeebe version to build the image for')
        stringParam('REVISION', '', 'The Git commit used to build the image; can be omitted except for releases')
        stringParam('DATE', '', 'The ISO-8601 date when the packaged artifact was built; can be omitted except for releases')
        booleanParam('IS_LATEST', false, 'Should the docker image be tagged as camunda/zeebe:latest?')
        booleanParam('PUSH', false, 'Should the docker image be pushed to docker hub?')
        booleanParam('VERIFY', false', 'Should we verify the Docker image before pushing it?')
    }
}
