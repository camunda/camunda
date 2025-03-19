export RELEASE_VERSION="8.6.11-test-SNAPSHOT"
export DEVELOPMENT_VERSION="8.6.11-test2-SNAPSHOT"
export PUSH_CHANGES=true
export GITHUB_TOKEN_USR=

./mvnw release:prepare -B \
            -Dresume=false \
            -Dtag=${RELEASE_VERSION} \
            -DreleaseVersion=${RELEASE_VERSION} \
            -DdevelopmentVersion=${DEVELOPMENT_VERSION} \
            -DpushChanges=${PUSH_CHANGES} \
            -DremoteTagging=${PUSH_CHANGES} \
            -DcompletionGoals="spotless:apply" \
            -D skipOptimize \
            -DskipTests \
            -Dskip.docker \
            -X \
            -DwaitBeforeTagging=0 \
            -P-autoFormat \
            -DpreparationGoals="" \
            -Darguments=''
