#!/bin/bash
set -ex
echo "DRY_RUN=${DRY_RUN}"

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git remote set-url origin "https://${GITHUB_APP_ID}:${GITHUB_TOKEN}@github.com/camunda/camunda-optimize.git"
git fetch
git checkout $BRANCH

SKIP_PUSH_ARTIFACTS=""
if [ "$DRY_RUN" = "true" ]; then
    SKIP_PUSH_ARTIFACTS="true"
    echo "Not pushing any artifacts to nexus."
else
    SKIP_PUSH_ARTIFACTS="false"
    echo "The generated artifacts will be pushed to nexus."
fi
echo "SKIP_PUSH_ARTIFACTS=${SKIP_PUSH_ARTIFACTS}"


echo "Starting artifact creation:"
# We are passing the arguments -DskipTests -DskipNexusStagingDeployMojo=false -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
# twice. Once on the maven command and once on the maven release plugin. Reason for that is because both maven and the maven release pipeline attempt these operations separately
# upon invokation (eg maven will by default try to run the tests and that's why we use the -dskipTests argument, but also the maven release library is running the tests by default
# when generating an artifact). Read more info here: https://maven.apache.org/maven-release/maven-release-plugin/prepare-mojo.html
mvn -DpushChanges=false -DskipTests -Prelease,engine-latest release:prepare release:perform -Dtag="${RELEASE_VERSION}" -DreleaseVersion="${RELEASE_VERSION}" -DdevelopmentVersion="${DEVELOPMENT_VERSION}" -Darguments="-DskipTests -DskipNexusStagingDeployMojo=${SKIP_PUSH_ARTIFACTS} -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn" -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

echo "Artifacts created:"
ls -1 distro/target

if [ "$DRY_RUN" = "true" ]; then
    echo "Not pushing git commits to release branch!"
else
    echo "Pushing git commits to release branch!"
    git push origin $BRANCH
fi
