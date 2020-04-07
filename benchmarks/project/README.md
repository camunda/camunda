# Project

This project contains code for the starter and worker, which are used during our benchmarks.

## Build docker images for benchmark application

### Update the Zeebe version (only required once a release cycle)

```
mvn versions:set-property -DgenerateBackupPoms=false -Dproperty=version.zeebe -DnewVersion=X.Y.Z-SNAPSHOT
```

### Build and push the images

```
docker-compose build
docker-compose push
```
