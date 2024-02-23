# Building Docker Images

This document contains information on how to build native zeebe docker images for other platforms
than the default amd64 architecture (e.g. for Apple Silicon/M1 based Macs).

> **Note**
> These instructions are meant for local development and testing (especially on ARM-based machines),
> not for production use.

## Optional: Get the Distball

Either build the project yourself or download an existing artifact, specifying the desired version:

```bash
mvn dependency:get -B \
    -DremoteRepositories="https://artifacts.camunda.com/artifactory/zeebe-io/" \
    -DgroupId="io.camunda" -DartifactId="camunda-zeebe" \
    -Dversion="8.1.2" -Dpackaging="tar.gz" -Dtransitive=false

mvn dependency:copy -B \
    -Dartifact="io.camunda:camunda-zeebe:8.1.2:tar.gz" \
    -DoutputDirectory=./ \
    -Dmdep.stripVersion=true
```

## Make sure BuildKit is enabled

The Dockerfile for Zeebe
requires [BuildKit](https://docs.docker.com/build/buildkit/#getting-started),
which is enabled by default if Docker Desktop is installed.
On Linux you may need to enable it explictly by setting the environment variable

```bash
DOCKER_BUILDKIT=1
```

## Build the Image

Now build the image for your local platform (supported for AMD64 and ARM64):

```bash
docker build --build-arg DIST=build -t my-zeebe:latest .
```

If you already built or downloaded a distball you can use that too:

```bash
docker build --build-arg DISTBALL=camunda-zeebe.tar.gz -t my-zeebe:latest .
```

If you need a specific version of
the [`eclipse-temurin:17-jre-focal`](https://hub.docker.com/layers/library/eclipse-temurin/17-jre-focal/images/sha256-e7fe469c4e729ff0ed6ff464f41eaff0e4cb9b6fe7efe71754d8935c8118eb87?context=explore)
base image,
you can override the default BASE_DIGEST[_AMD64|ARM64] depending on your local architecture like
that:
amd64: `--build-arg BASE_DIGEST_AMD64="sha256:00a5775f5eb7c24a19cb76ded742cbfcc50c61f062105af9730dadde217e4390"`
arm64: `--build-arg BASE_DIGEST_ARM64="sha256:ce46be0c4b4edd9f519e99ad68a6b5765abe577fbf1662d8ad2550838eb29823"`

## Use it

In your [docker-compose.yaml](../docker/compose/docker-compose.yaml), change `camunda/zeebe`
to `my-zeebe` to use the newly created image.
