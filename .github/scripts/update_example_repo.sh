#!/bin/bash
set -x

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git remote set-url origin https://$GITHUB_APP_ID:$GITHUB_TOKEN@github.com/camunda/camunda-optimize-examples.git
git fetch
git checkout $RELEASE_VERSION
git reset --hard origin/$RELEASE_VERSION
git pull

### adjust the readme and add the new version to the version overview
# find line where the version overview starts
first_line=$(grep -n "Optimize Version" README.md | head -n 1 | cut -d: -f1)

if [ -z "$first_line" ]; then
  echo "Could not find start of the version overview. Aborting instead!"
  exit 1
fi

# find line where the version overview ends
last_line=$(sed -n "$first_line,/^$/=" README.md | tail -n 1)

if [ -z "last_line" ]; then
  echo "Could not find end of the version overview. Aborting instead!"
  exit 1
fi

# find lastest line of the version overview
line_to_insert=$(sed -n "$first_line,/^| Latest/=" README.md | tail -n 1)

if [ -z "line_to_insert" ] || [ "$line_to_insert" -le "$(($first_line+1))" ] || [ "$line_to_insert" -ge "$((last_line+1))" ]; then
  echo "Could not find line to insert the Optimize version to overview. Aborting instead!"
  exit 1
fi

# add the new entry for the released version to the version overview
version=$RELEASE_VERSION
sed "$line_to_insert a | $version | [$version tag](https://github.com/camunda/camunda-optimize-examples/tree/$version)| 'git checkout $version'|" README.md > RESULT.md
rm README.md
mv RESULT.md README.md

# update the optimize version in pom to the release version
sed -i "s/optimize.version>.*</optimize.version>$RELEASE_VERSION</g" pom.xml
# update the optimize-examples version in pom to release version
mvn versions:set versions:commit -DnewVersion=$RELEASE_VERSION

git add -u
git commit -m "chore(release): $RELEASE_VERSION"

# create tag for the new Optimize version
git tag -a $RELEASE_VERSION -m "Tag for version Optimize $RELEASE_VERSION"
git push origin $RELEASE_VERSION

# update the optimize version in pom to development version
sed -i "s/optimize.version>.*</optimize.version>$DEVELOPMENT_VERSION</g" pom.xml
# update the optimize-examples version in pom to development version
mvn versions:set versions:commit -DnewVersion=$DEVELOPMENT_VERSION

# push the changes
git add -u
git commit -m "chore(release): update pom to snapshot for next development version"
git push origin $RELEASE_VERSION