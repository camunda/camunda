## Pre-requisites

To run the upgradability tests, a Docker image for Zeebe must be built with the tag 'current-test'. To do that you can run (in the zeebe-io/zeebe dir):

```
docker build --build-arg DISTBALL=dist/target/camunda-zeebe*.tar.gz -t camunda/zeebe:current-test --target app .
```

## Debugging

If you need to debug a remote container, you can use the `RemoteDebugger#configureContainer` utility
method, or use the `withDebugger` flagged when starting your `ContainerState`, which will do it for
you.

By using this utility, it will launch the container with a remote debugger which will wait for your
local debugger to connect before starting the container. See
[this IntelliJ tutorial](https://www.jetbrains.com/help/idea/tutorial-remote-debug.htm) on how to
use a remote debugger with IntelliJ.

### Limitations

One current limitation is that if your code relies on timeouts (which, for example, the update
tests do), then when your debugger is at a break point, only the remote JVM is stopped - this means
your tests will definitely time out. The current workaround is to simply increase all timeouts,
but in the long term we should find a solution for this. This means not only the startup timeout of
the container, but also any use of `Awaitility` in the tests themselves, e.g. waiting for a message
to be published.
