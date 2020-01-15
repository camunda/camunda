mvn versions:set-property -DgenerateBackupPoms=false -Dproperty=backwards.compat.version -DnewVersion=${RELEASE_VERSION}

git commit -am "chore(project): update backwards compatibility version"
