// vim: set filetype=groovy:

def jobName = 'zeebe-DISTRO-docker'
def repository = 'zeebe'
def gitBranch = 'develop'

def dockerHubUpload =
'''\
#!/bin/bash -xeu

# clear docker host env set by jenkins job
unset DOCKER_HOST

VERSION=${RELEASE_VERSION}

if [ "${RELEASE_VERSION}" = "SNAPSHOT" ]; then
    VERSION=$(curl -sL https://raw.githubusercontent.com/zeebe-io/zeebe/develop/pom.xml | grep '<version>' | head -n 1 | cut -d '>' -f 2 | cut -d '<' -f 1  )
fi

echo "Downloading maven"
mkdir -p /tmp/maven
curl -sL http://mirror.synyx.de/apache/maven/maven-3/3.6.0/binaries/apache-maven-3.6.0-bin.tar.gz | tar xzvf - -C /tmp/maven --strip 1

echo "Downloading Zeebe distribution ${VERSION}."
/tmp/maven/bin/mvn dependency:get -B \
    -DremoteRepositories="camunda-nexus::::https://app.camunda.com/nexus/content/repositories/public" \
    -DgroupId="io.zeebe" -DartifactId="zeebe-distribution" \
    -Dversion="${VERSION}" -Dpackaging="tar.gz" -Dtransitive=false

/tmp/maven/bin/mvn dependency:copy -B \
    -Dartifact="io.zeebe:zeebe-distribution:${VERSION}:tar.gz" \
    -DoutputDirectory=${WORKSPACE} \
    -Dmdep.stripVersion=true


echo "Building Zeebe Docker image ${RELEASE_VERSION}."
docker build --no-cache -t camunda/zeebe:${RELEASE_VERSION} --build-arg DISTBALL=zeebe-distribution.tar.gz -f Dockerfile.debian .

echo "Authenticating with DockerHub and pushing image."
docker login --username ${DOCKER_HUB_USERNAME} --password ${DOCKER_HUB_PASSWORD} --email ci@camunda.com

docker push camunda/zeebe:${RELEASE_VERSION}

if [ "${IS_LATEST}" = "true" ]; then
    docker tag -f camunda/zeebe:${RELEASE_VERSION} camunda/zeebe:latest
    docker push camunda/zeebe:latest
fi
'''

freeStyleJob(jobName)
{
    scm
    {
        git
        {
            remote
            {
                github 'zeebe-io/' + repository, 'ssh'
                credentials 'camunda-jenkins-github-ssh'
            }
            branch gitBranch
            extensions
            {
                localBranch gitBranch
            }
        }
    }

    label 'dind'

    parameters
    {
        stringParam('RELEASE_VERSION', 'SNAPSHOT', 'Docker image tag')
        booleanParam('IS_LATEST', false, 'If <strong>TRUE</strong> the image is also tagged with latest')
    }

    wrappers
    {
        timestamps()

        timeout
        {
            absolute 10
        }

        credentialsBinding {
          usernamePassword('DOCKER_HUB_USERNAME', 'DOCKER_HUB_PASSWORD', 'camundajenkins-dockerhub')
        }
    }

    steps {
        shell dockerHubUpload
    }

    publishers
    {

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
    logRotator(-1, 5, -1, 1)

}
