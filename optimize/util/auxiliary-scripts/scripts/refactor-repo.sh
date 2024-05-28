#!/bin/bash
#
# options
#
# DO_COMMIT="true" optimize-refactor.sh
#   commits each partial result
#
set -e

# this script only works on OSX, currently
if [[ $(uname) != "Darwin" ]]; then
    echo "Error:"
    echo "  This script works on macOS only."
    echo
    exit 1
fi

# make sure we are in the right directory
current_dir=$(pwd)
if [[ ! "$current_dir" == *"/camunda-optimize" ]]; then
    echo "Error:"
    echo "  Wrong current directory."
    echo "  You need to be within the camunda-optimize repo."
    echo
    exit 2
fi

# make sure all of the required files exist
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
if [ ! -e "$SCRIPT_DIR/resources/new-parent-pom.xml" ]; then
  echo "Error:"
  echo "  Missing required file: $SCRIPT_DIR/resources/new-parent-pom.xml"
  echo
  exit 3
fi

# create optimize subdirectory
mkdir optimize

if [[ "$DO_COMMIT" == "true" ]]; then
  touch optimize/wip
  git add optimize/wip
  git commit -m "refactor: move Optimize code into subdirectory"
fi

#################################################################
#             Move backend -> optimize/backend                  #
#################################################################

# move directory
mv backend optimize/

# patch parent pom.xml
filename="pom.xml"
old_line="<module>backend</module>"
new_line="<module>optimize/backend</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch child pom.xml
filename="optimize/backend/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
    <version>3.14.0-SNAPSHOT<\/version>\
    <relativePath>..\/..\/pom.xml<\/relativePath>
' $filename

# patch README.md
filename="README.md"
old_line="(backend/README.md)"
new_line="(optimize/backend/README.md)"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="-pl backend"
new_line="-pl optimize/backend"
sed -i "" "s#$old_line#$new_line#" $filename

# patch docker-compose.postgresql.yml
filename="docker-compose.postgresql.yml"
old_line="\.\/backend\/"
new_line="\.\/optimize\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch docker-compose.yml
filename="docker-compose.yml"
old_line="\.\/backend\/"
new_line="\.\/optimize\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/actions/compose/docker-compose.cambpm.yml
filename=".github/actions/compose/docker-compose.cambpm.yml"
old_line="\.\.\/backend\/"
new_line="\.\.\/optimize\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/actions/compose/docker-compose.smoketest.release.yml
filename=".github/actions/compose/docker-compose.smoketest.release.yml"
old_line="\.\.\/backend\/"
new_line="\.\.\/optimize\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/actions/compose/docker-compose.smoketest.yml
filename=".github/actions/compose/docker-compose.smoketest.yml"
old_line="\.\.\/backend\/"
new_line="\.\.\/optimize\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/actions/setup-maven/action.yml
filename=".github/actions/setup-maven/action.yml"
old_line="backend/target/classes/"
new_line="optimize/backend/target/classes/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-code-scanning-java.yml
filename=".github/workflows/optimize-code-scanning-java.yml"
old_line="backend/src/\*\*/\*.java"
new_line="optimize/backend/src/\*\*/\*.java"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-connect-to-secured-es.yml
filename=".github/workflows/optimize-connect-to-secured-es.yml"
old_line="\.\/backend\/"
new_line="\.\/optimize\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-pmd.yml
filename=".github/workflows/optimize-pmd.yml"
old_line="sourcePath: 'backend/src/main/java'"
new_line="sourcePath: 'optimize/backend/src/main/java'"
sed -i "" "s#$old_line#$new_line#" $filename

# patch client/src/setupTests.ts
filename="client/src/setupTests.ts"
old_line="/backend/src/"
new_line="/optimize/backend/src/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch client/scripts/start-backend.js
filename="client/scripts/start-backend.js"
old_line="'..', 'backend', 'target'"
new_line="'..', 'optimize', 'backend', 'target'"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="-pl backend"
new_line="-pl optimize/backend"
sed -i "" "s#$old_line#$new_line#" $filename

# patch distro/assembly-base-component.xml
filename="distro/assembly-base-component.xml"
old_line="/backend/"
new_line="/optimize/backend/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch util/optimize-reimport-preparation/pom.xml
filename="util/optimize-reimport-preparation/pom.xml"
old_line="<argument>backend/pom.xml</argument>"
new_line="<argument>optimize/backend/pom.xml</argument>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch util/dependency-doc-creation/createOptimizeDependencyFiles.sh
filename="util/dependency-doc-creation/createOptimizeDependencyFiles.sh"
old_line="cd ./backend"
new_line="cd ./optimize/backend"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-ci.yml
filename=".github/workflows/optimize-ci.yml"
old_line="-pl backend"
new_line="-pl optimize/backend"
sed -i "" "s#$old_line#$new_line#" $filename

# patch optimize/backend/src/it/java/org/camunda/optimize/rest/InstantPreviewDashboardIT.java
filename="optimize/backend/src/it/java/org/camunda/optimize/rest/InstantPreviewDashboardIT.java"
old_line='FRONTEND_EXTERNAL_RESOURCES_PATH = "../client/public"'
new_line='FRONTEND_EXTERNAL_RESOURCES_PATH = "../../client/public"'
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-test-cloud.yml
filename=".github/workflows/optimize-e2e-test-cloud.yml"
old_line="-pl backend"
new_line="-pl optimize/backend"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-tests-browserstack.yml
filename=".github/workflows/optimize-e2e-tests-browserstack.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-tests.yml
filename=".github/workflows/optimize-e2e-tests.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-engine-compatibility.yml
filename=".github/workflows/optimize-engine-compatibility.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-es-compatibility.yml
filename=".github/workflows/optimize-es-compatibility.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-history-cleanup-performance.yml
filename=".github/workflows/optimize-history-cleanup-performance.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-import-dynamic-data-performance.yml
filename=".github/workflows/optimize-import-dynamic-data-performance.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-import-mediator-permutation.yml
filename=".github/workflows/optimize-import-mediator-permutation.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-java-compatibility.yml
filename=".github/workflows/optimize-java-compatibility.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-os-compatibility.yml
filename=".github/workflows/optimize-os-compatibility.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-zeebe-compatibility.yml
filename=".github/workflows/optimize-zeebe-compatibility.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch optimize/backend/TESTING.md (docs)
filename="optimize/backend/TESTING.md"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  rm optimize/wip
  git add .
  git commit -m "move backend -> optimize/backend"
fi

#################################################################
#                 Move util -> optimize/util                    #
#################################################################

mv util optimize/

# patch parent pom.xml
filename="pom.xml"
old_line="<module>util</module>"
new_line="<module>optimize/util</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch child pom.xml
filename="optimize/util/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
        <version>3.14.0-SNAPSHOT<\/version>\
        <relativePath>..\/..\/pom.xml<\/relativePath>
' $filename

# patch .github/workflows/optimize-ci.yml
filename=".github/workflows/optimize-ci.yml"
old_line="util"
new_line="optimize/util"
sed -i "" "s#$old_line#$new_line#" $filename

# patch distro/assembly-base-component.xml
filename="distro/assembly-base-component.xml"
old_line="\.\.\/util"
new_line="\.\.\/optimize\/util"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-code-scanning-java.yml
filename=".github/workflows/optimize-code-scanning-java.yml"
old_line="util"
new_line="optimize\/util"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move util -> optimize/util"
fi

#################################################################
#              Move plugins -> optimize/plugins                 #
#################################################################

mv plugins optimize/

# patch parent pom.xml
filename="pom.xml"
old_line="<module>plugins</module>"
new_line="<module>optimize/plugins</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch child pom.xml
filename="optimize/plugins/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
    <version>3.14.0-SNAPSHOT<\/version>\
    <relativePath>..\/..\/pom.xml<\/relativePath>
' $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move plugins -> optimize/plugins"
fi

#################################################################
#              Rename distro -> optimize-distro                 #
#################################################################

mv distro optimize-distro

# patch pom.xml
filename="pom.xml"
old_line="<module>distro</module>"
new_line="<module>optimize-distro</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .dockerignore
filename=".dockerignore"
echo '!optimize-distro/target/*.tar.gz' >> $filename
echo '!target/checkout/optimize-distro/target/*.tar.gz' >> $filename

# patch Dockerfile
filename="Dockerfile"
old_line="distro\/"
new_line="optimize-distro\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch README.md
filename="README.md"
old_line="distro"
new_line="optimize-distro"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/scripts/execute-release.sh
filename=".github/scripts/execute-release.sh"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-release-optimize.yml
filename=".github/workflows/optimize-release-optimize.yml"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "rename distro -> optimize-distro"
fi

#################################################################
#                   Move qa -> optimize/qa                      #
#################################################################

mv qa optimize/

# parch parent pom.xml
filename="pom.xml"
old_line="<module>qa</module>"
new_line="<module>optimize/qa</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch child pom.xml
filename="optimize/qa/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
        <version>3.14.0-SNAPSHOT<\/version>\
        <relativePath>..\/..\/pom.xml<\/relativePath>
' $filename

# patch .gitignore
filename=".gitignore"
old_line="qa\/engine"
new_line="optimize\/qa\/engine"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-hadolint.yml
filename=".github/workflows/optimize-hadolint.yml"
old_line="qa"
new_line="optimize\/qa"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-ci.yml
filename=".github/workflows/optimize-ci.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-cluster-test.yml
filename=".github/workflows/optimize-cluster-test.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-connect-to-secured-es.yml
filename=".github/workflows/optimize-connect-to-secured-es.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-test-cloud.yml
filename=".github/workflows/optimize-e2e-test-cloud.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-tests-browserstack.yml
filename=".github/workflows/optimize-e2e-tests-browserstack.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-tests.yml
filename=".github/workflows/optimize-e2e-tests.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-history-cleanup-performance.yml
filename=".github/workflows/optimize-history-cleanup-performance.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-import-dynamic-data-performance.yml
filename=".github/workflows/optimize-import-dynamic-data-performance.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-import-mediator-permutation.yml
filename=".github/workflows/optimize-import-mediator-permutation.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-java-compatibility.yml
filename=".github/workflows/optimize-java-compatibility.yml"
sed -i "" "s#$old_line#$new_line#" $filename
old_line=",qa"
new_line=",optimize\/qa"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-upgrade-data-performance.yml
filename=".github/workflows/optimize-upgrade-data-performance.yml"
old_line="qa"
new_line="optimize/qa"
sed -i "" "s#$old_line#$new_line#" $filename

# patch client/scripts/generate-data.js
filename="client/scripts/generate-data.js"
sed -i "" "s#$old_line#$new_line#" $filename

# patch client/scripts/start-backend.js
filename="client/scripts/start-backend.js"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move qa -> optimize/qa"
fi

#################################################################
#              Move upgrade -> optimize/upgrade                 #
#################################################################

mv upgrade optimize/

# patch parent pom.xml
filename="pom.xml"
old_line="<module>upgrade</module>"
new_line="<module>optimize/upgrade</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch child pom.xml
filename="optimize/upgrade/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
    <version>3.14.0-SNAPSHOT<\/version>\
    <relativePath>..\/..\/pom.xml<\/relativePath>
' $filename

# patch .github/workflows/optimize-generate-upgrade-plan.yml
filename=".github/workflows/optimize-generate-upgrade-plan.yml"
old_line="upgrade\/src"
new_line="optimize\/upgrade\/src"
sed -i "" "s#$old_line#$new_line#" $filename

# patch optimize-distro/assembly-base-component.xml
filename="optimize-distro/assembly-base-component.xml"
old_line="\.\.\/upgrade"
new_line="\.\.\/optimize\/upgrade"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-ci.yml
filename=".github/workflows/optimize-ci.yml"
old_line="pl upgrade"
new_line="pl optimize/upgrade"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move upgrade -> optimize/upgrade"
fi

#################################################################
#               Move client -> optimize/client                  #
#################################################################

mv client optimize/

# patch parent pom.xml
filename="pom.xml"
old_line="<module>client</module>"
new_line="<module>optimize/client</module>"
sed -i "" "s#$old_line#$new_line#" $filename

# patch child pom.xml
filename="optimize/client/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
    <version>3.14.0-SNAPSHOT<\/version>\
    <relativePath>..\/..\/pom.xml<\/relativePath>
' $filename

# patch .gitignore
filename=".gitignore"
old_line="client\/"
new_line="optimize\/client\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-backend-linting.yml
filename=".github/workflows/optimize-backend-linting.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-deploy-stage-envs.yml
filename=".github/workflows/optimize-deploy-stage-envs.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-test-cloud.yml
filename=".github/workflows/optimize-e2e-test-cloud.yml"
old_line="client"
new_line="optimize/client"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-tests-browserstack.yml
filename=".github/workflows/optimize-e2e-tests-browserstack.yml"
old_line="client\/"
new_line="optimize\/client\/"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="\.\/client"
new_line="\.\/optimize\/client"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-e2e-tests.yml
filename=".github/workflows/optimize-e2e-tests.yml"
old_line="client"
new_line="optimize/client"
sed -i "" "s#$old_line#$new_line#g" $filename

# patch .github/workflows/optimize-unit-tests.yml
filename=".github/workflows/optimize-unit-tests.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch optimize/client/src/setupTests.ts
filename="optimize/client/src/setupTests.ts"
old_line="import translation from '../../optimize/backend/src/main/resources/localization/en.json';"
new_line="import translation from '../../../optimize/backend/src/main/resources/localization/en.json';"
sed -i "" "s#$old_line#$new_line#" $filename

# revert optimize/backend/src/it/java/org/camunda/optimize/rest/InstantPreviewDashboardIT.java
filename="optimize/backend/src/it/java/org/camunda/optimize/rest/InstantPreviewDashboardIT.java"
new_line='FRONTEND_EXTERNAL_RESOURCES_PATH = "../client/public"'
old_line='FRONTEND_EXTERNAL_RESOURCES_PATH = "../../client/public"'
sed -i "" "s#$old_line#$new_line#" $filename

# adjust optimize/client/scripts/start-backend.js
filename="optimize/client/scripts/start-backend.js"
old_line="'..', 'optimize', 'backend', 'target'"
new_line="'..', 'backend', 'target'"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move client -> optimize/client"
fi

#################################################################
#               Move pom.xml -> optimize/pom.xml                #
#################################################################

mv pom.xml optimize/

# adjust modules
filename="optimize/pom.xml"
old_line="\<module>optimize\/"
new_line="\<module>"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="\<module>optimize-distro\<\/module>"
new_line="\<module>..\/optimize-distro\<\/module>"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="\<artifactId>parent\<\/artifactId>"
new_line="\<artifactId>optimize-parent\<\/artifactId>"
sed -i "" "s#$old_line#$new_line#" $filename

# license header dependency
old_line="\/license\/header.txt"
new_line="\/..\/license\/header.txt"
sed -i "" "s#$old_line#$new_line#" $filename

# adjust children pom.xml
children=("optimize-distro/pom.xml" "optimize/client/pom.xml" "optimize/util/pom.xml"
    "optimize/plugins/pom.xml" "optimize/backend/pom.xml" "optimize/qa/pom.xml"
    "optimize/upgrade/pom.xml")
for filename in "${children[@]}"; do
  sed -i '' "/<relativePath>..\/..\/pom.xml<\/relativePath>/d" "$filename"
  old_line="\<artifactId>parent\<\/artifactId>"
  new_line="\<artifactId>optimize-parent\<\/artifactId>"
  sed -i "" "s#$old_line#$new_line#" $filename
done

# adjust relative path for optimize-distro
filename="optimize-distro/pom.xml"
sed -i '' '/<version>3.14.0-SNAPSHOT<\/version>/c\
    <version>3.14.0-SNAPSHOT<\/version>\
    <relativePath>../optimize/pom.xml</relativePath>
' $filename

# create new parent pom.xml
cp "$SCRIPT_DIR/resources/new-parent-pom.xml" ./pom.xml

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "pom.xml -> optimize/pom.xml"
fi

#################################################################
#                    Move c4 -> optimize/c4                     #
#################################################################

mv c4 optimize/

# patch .github/workflows/optimize-check-c4.yml
filename=".github/workflows/optimize-check-c4.yml"
old_line="c4"
new_line="optimize\/c4"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-publish-c4.yml
filename=".github/workflows/optimize-publish-c4.yml"
old_line="Optimize publish optimize\/c4 to npm"
new_line="Optimize publish c4 to npm"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="publish-c4"
new_line="publish-optimize-c4"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="\.\/c4"
new_line="\.\/optimize\/c4"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="c4\/"
new_line="optimize\/c4\/"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move c4 -> optimize/c4"
fi


#################################################################
#                Move docker -> optimize/docker                 #
#################################################################

mv docker optimize/
mv Dockerfile optimize.Dockerfile

# patch optimize.Dockerfile
filename="optimize.Dockerfile"
old_line="COPY docker/bin"
new_line="COPY \./optimize/docker/bin"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/renovate.json5
filename=".github/renovate.json5"
old_line="docker\/"
new_line="optimize\/docker\/"
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/scripts/build-docker-images.sh
filename=".github/scripts/build-docker-images.sh"
old_line="docker\/"
new_line="optimize\/docker\/"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="    \."
new_line="    -f optimize.Dockerfile \."
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/scripts/build-release-docker-images.sh
filename=".github/scripts/build-release-docker-images.sh"
old_line="docker\/"
new_line="optimize\/docker\/"
sed -i "" "s#$old_line#$new_line#" $filename
old_line="    \."
new_line="    -f optimize.Dockerfile ."
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-hadolint.yml
filename=".github/workflows/optimize-hadolint.yml"
old_line="\"Dockerfile\""
new_line="\"optimize.Dockerfile\""
sed -i "" "s#$old_line#$new_line#" $filename

# patch .github/workflows/optimize-ci.yml
filename=".github/workflows/optimize-ci.yml"
old_line="        context: \."
new_line1="$old_line"
new_line2="        file: optimize\.Dockerfile"
sed -i '' "/$old_line/c\\
$new_line1\\
$new_line2
" $filename

# patch .dockerignore
filename=".dockerignore"
old_line="\!docker"
new_line="\!optimize\/docker"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "move docker -> optimize/docker"
fi

#################################################################
#                         Finishing up                          #
#################################################################

mv zeebe-application.yml optimize/
mv docker-compose.ccsm-with-optimize.yml optimize/
mv docker-compose.ccsm-without-optimize.yml optimize/
mv docker-compose.postgresql.yml optimize/
mv docker-compose.yml optimize/

# patch optimize/docker-compose.ccsm-with-optimize.yml
filename="optimize/docker-compose.ccsm-with-optimize.yml"
old_line="zeebe-application\.yml"
new_line="optimize\/zeebe-application\.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch optimize/docker-compose.ccsm-without-optimize.yml
filename="optimize/docker-compose.ccsm-without-optimize.yml"
sed -i "" "s#$old_line#$new_line#" $filename

# patch/revert optimize/docker-compose.yml
filename="optimize/docker-compose.yml"
old_line="\.\/optimize\/backend\/"
new_line="\.\/backend\/"
sed -i "" "s#$old_line#$new_line#" $filename

if [[ "$DO_COMMIT" == "true" ]]; then
  git add .
  git commit -m "finishing up"
fi
