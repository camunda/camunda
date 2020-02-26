if [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  mvn versions:set-property -DgenerateBackupPoms=false -Dproperty=backwards.compat.version -DnewVersion=${RELEASE_VERSION}
  gocompat save --path=clients/go/.gocompat.json clients/go/...

  FILE=$(mvn help:evaluate -Dexpression=ignored.changes.file -q -DforceStdout)

  rm -f clients/java/$FILE test/$FILE exporter-api/$FILE protocol/$FILE

  git commit -am "chore(project): update backwards compatibility version"
else
  echo "Version $RELEASE_VERSION"
fi
