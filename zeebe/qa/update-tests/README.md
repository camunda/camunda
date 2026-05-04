see [Testing Guide](https://github.com/camunda/camunda/blob/main/docs/testing/acceptance.md#rolling-update-tests) for more information

## How the current version is run

| Test                                                | Previous version | Current version              |
|-----------------------------------------------------|------------------|------------------------------|
| `SnapshotTest`, `NoSnapshotTest`                    | Docker container | In-process via Spring Boot   |
| `OldGatewayTest`                                    | Docker container | In-process via Spring Boot   |
| `BackupCompatibilityAcceptance` (S3, GCS, Azure)    | Docker container | In-process via Spring Boot   |
| `RollingUpdateTest`                                 | Docker container | Docker container (see below) |

The "current version" is loaded as the test classpath itself. There is no need to pre-build a
`camunda/camunda:current-test` Docker image for these tests.

`RollingUpdateTest` is the exception: it stops a node in a multi-broker cluster, replaces its
image with the current version, and lets it rejoin gossip. This is fundamentally a deployment-level
test that only makes sense with real container images. Before running it, build the image (from
the camunda/camunda dir):

```
docker build --build-arg DISTBALL='dist/target/camunda-zeebe*.tar.gz' -t camunda/camunda:current-test --target app .
```

Override the image used for the current version with the `CAMUNDA_TEST_DOCKER_IMAGE` env var.

## Debugging

For the previous-version container or for `RollingUpdateTest`, you can use
`RemoteDebugger#configureContainer` (or the `withDebugger` flag of `ContainerState`) to launch the
container with a remote debugger that waits for your local debugger to attach. See
[this IntelliJ tutorial](https://www.jetbrains.com/help/idea/tutorial-remote-debug.htm) for setup.

For the current-version side of `SnapshotTest`, `NoSnapshotTest`, and `OldGatewayTest`, the broker
runs in the same JVM as the test, so a regular run-with-debugger from your IDE is enough — no
remote attach needed.

### Limitations

If your code relies on timeouts (which, for example, the update tests do), then when your debugger
is at a break point, only the remote JVM is stopped — this means your tests will definitely time
out. The current workaround is to simply increase all timeouts, but in the long term we should find
a solution for this. This means not only the startup timeout of the container, but also any use of
`Awaitility` in the tests themselves, e.g. waiting for a message to be published.
