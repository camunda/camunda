# Building Docker Images

This document contains information on how to build native zeebe docker images for other platforms than the default amd64 architecture (e.g. for Apple Silicon/M1 based Macs).

> **Note**  
> These instructions are meant for local development and testing (especially on ARM-based machines), not for production use.

## Get the Distball

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

## Build the Image

Now build the image for your local platform:

```bash
docker build --build-arg DISTBALL=camunda-zeebe.tar.gz --build-arg BASE_SHA="" -t my-zeebe:latest .
```

By default, the BASE_SHA argument in the Dockerfile points to an amd64 base image. Overriding the argument with an empty string - like in the above command - will automatically use your local system architecture for the base image.

If you need a specific version of the [`eclipse-temurin:17-jre-focal`](https://hub.docker.com/layers/library/eclipse-temurin/17-jre-focal/images/sha256-e7fe469c4e729ff0ed6ff464f41eaff0e4cb9b6fe7efe71754d8935c8118eb87?context=explore) base image, you can override the default BASE_DIGEST like that: `--build-arg BASE_DIGEST="@sha256:fce37e5146419a158c2199c6089fa39b92445fb2e66dc0331f8591891239ea3b"`

## Use it

In your [docker-compose.yaml](../docker/compose/docker-compose.yaml), change `camunda/zeebe` to `my-zeebe` to use the newly created image.
