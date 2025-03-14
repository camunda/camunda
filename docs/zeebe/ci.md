# Continuous Integration Guide

This is a small guide for things useful around our continuous integration setup.

## Github Action

### Maven repository cache

Within
our [Github action to setup build](/Users/megglos/git/zeebe/.github/actions/setup-build/action.yml)
we make use of [action/cache](https://github.com/actions/cache) to store the content of
the maven repository and share it across jobs and even branches. Primarily to speedup builds and
reduce the
risks of failures due to network hiccups to remote repositories (rare but happened).

#### Delete Maven repository caches

In case you encounter a corrupted cache which you suspect to be the cause of a build failure you may
delete that cache to force a clean rebuild of it.

[Github Actions Cache](https://github.com/actions/gh-actions-cache) provides convenient tooling for
doing so.

You can easily list existing caches with:

```bash
gh actions-cache list

Linux-maven-default-dddf7912807d96783a168b6dda5c5b639c6eb183bfedaab524c6e34f695afba4        586.20 MB  refs/pull/10550/merge     10 minutes ago
```

and delete them via:

```bash
gh actions-cache delete Linux-maven-default-dddf7912807d96783a168b6dda5c5b639c6eb183bfedaab524c6e34f695afba4
```

To determine the key of the cache used you can take a look into the log of the setup-build job.

```
...
Run actions/cache@v3
  with:
    path: ~/.m2/repository
    key: Linux-maven-default-dddf7912807d96783a168b6dda5c5b639c6eb183bfedaab524c6e34f695afba4
    restore-keys: Linux-maven-default-

  env:
    TC_CLOUD_TOKEN: ***
    TC_CLOUD_CONCURRENCY: 4
    ZEEBE_TEST_DOCKER_IMAGE: localhost:5000/camunda/zeebe:current-test
    JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.4-101/x64
Received 0 of 614674276 (0.0%), 0.0 MBs/sec
...
```

### Container-based tests (Testcontainers)

We make heavy use of container-based tests when running integration tests in Zeebe, primarily by leveraging [Testcontainers](https://testcontainers.org).

> This applies only to jobs which run integration tests, such as the update test, or the integration test jobs.

When you run the tests locally, they will use your local Docker environment. However, as part of our CI pipeline, we make use of their SaaS offering, Testcontainers Cloud (TCC). This allows us to decouple resource usage used by containers and provides a modest performance boost to our tests by running the containers on different machines than the ones where our tests run.

#### How it works

Grossly simplified, TCC works by setting up an agent which will act as a Docker daemon, but proxy requests to remote VMs. It will then write a [Testcontainers configuration file](https://www.testcontainers.org/features/configuration/) that the Testcontainers library will automatically use when a test is executed.

> Note that AtomicJar, the company behind TCC, specifically mentions that their product is not a Docker-as-a-Service product, and shouldn't be treated as such.

You will find documentation, account management, settings, secret tokens, etc., in the [Testcontainers Cloud dashboard](https://app.testcontainers.cloud/dashboard). If you haven't been invited yet and cannot see the dashboard, ask one of the Zeebe team members for an invite.

Make sure to join the private Slack channel Camunda has with AtomicJar around TCC. There you will find a private invite link, and can provide feedback, ask questions, and receive support.

> If you are not yet in the channel, anybody from the Zeebe team can invite you. Note that this is only for core team members.

#### Usage

To use TCC with GitHub Actions, we first need to define a few environment variables to your job.

```yaml
env:
  TC_CLOUD_LOGS_VERBOSE: true
  TC_CLOUD_CONCURRENCY: 4
  ZEEBE_TEST_DOCKER_IMAGE: localhost:5000/camunda/zeebe:current-test
```

Let's break this down a bit.

1. Ensure the agent logs are verbose. TCC is still a beta product, so we want to make sure we can provide as much feedback as possible when troubleshooting any issues.
2. Define how many concurrent remote VMs our tests can use to start containers on.
3. Specify the name of the image our container based tests should use.

You'll notice the image name starts with a URL, because we'll be using a local Docker registry. This is required because we want to use the image that we build as part of the pipeline, without pushing it to any public Docker registry. By using a local registry and exposing it to TCC, we can use "private" images.

To set up a local Docker registry, we'll use a [GitHub Actions service container](https://docs.github.com/en/actions/using-containerized-services/about-service-containers). Add the following to your job definition:

```yaml
services:
  registry:
    image: registry:2
    ports:
      - 5000:5000
```

With the registry set up, the next step is to push the Docker image we're building to it.

```yaml
- uses: ./.github/actions/build-platform-docker
  with:
    repository: localhost:5000/camunda/zeebe
    version: current-test
    push: true
    distball: ${{ steps.build-zeebe.outputs.distball }}
```

Finally, we set up the TCC agent. This can be added as a step in any workflow, provided the `TCC_CLOUD_TOKEN` secret is made available to the job.

```yaml
- name: Prepare Testcontainers Cloud agent
  if: env.TC_CLOUD_TOKEN != ''
  run: |
    curl -L -o agent https://app.testcontainers.cloud/download/testcontainers-cloud-agent_linux_x86-64
    chmod +x agent
    ./agent --private-registry-url=http://localhost:5000 '--private-registry-allowed-image-name-globs=*,*/*' > .testcontainers-agent.log 2>&1 &
    ./agent wait
  env:
    TC_CLOUD_TOKEN: ${{ secrets.TC_CLOUD_TOKEN }}
```

Let's break this down. This step is conditional on the authentication token being present. This is a simple way to have a kill switch to disable it, by either removing the secret, or removing its exposure from your workflow/job. Testcontainers will work normally with the local Docker daemon available on the node without the agent.

Next the agent is downloaded (always using the latest version) and started. Since we want to test our own image that we built and pushed to our local registry, we'll expose it to the remote VM. Finally, we'll log everything to a file which will be included in the job's test published artifacts in case of failure.

#### Troubleshooting

For questions, support, feedback, documentation, etc., around TCC, Camunda has a private Slack channel with AtomicJar. The complete Zeebe team should have been invited already, but in case you were not, ask any other team member to add you.

If we're having recurring problems which are blocking us from working, we can simply disable the step which runs the agent to disable TCC. Nothing else in the code will need to be changed. Do write in the Slack channel so the people behind TCC are aware and can help us troubleshoot the issue.

> Note that disabling TCC may impact your tests, as now everything runs on the same VM and shares the same resources, which may increase flakiness, for example. If this is the case, we could consider temporarily increasing the runner VMs.

When writing in the Slack channel, please provide the logs from the `Prepare Testcontainers Cloud agent` step, as well as the log file included in the job's uploaded artifacts (i.e. `.testcontainers-agent.log`), and a link to a failing run.

### Failing tests

At times, there will be tests which only fail intermittently. It's critical to determine if these
are real bugs or if they are simply flaky tests.

#### Determine flakiness

In case the CI reports a flaky test, you can perform the following steps to determine your next
action.

- Check for [existing Zeebe issues with the `kind/flake` label](https://github.com/camunda/camunda/issues?q=is%3Aopen+is%3Aissue+label%3Akind%2Fflake) that report the same flaky test.
- If such an issue exists, comment in the issue about the re-occurrence. Provide a link to the CI build where the flaky test was reported.
- If the flaky test hasn't been reported in a Zeebe issue, try to determine if the flakiness was introduced through your code changes.
  - If the flaky test is unrelated to your code changes, [create a new "Unstable test" issue](https://github.com/camunda/camunda/issues/new?assignees=&labels=kind%2Fflake&template=unstable_test.md&title=). Fill out the provided issue template with as much information about the flaky test.
  - If the flaky test is related to your code changes, you should fix the flakiness before merging your code changes. You can follow the sections below on what to do in order to reproduce and root cause the problem.

#### Reproduce locally

Your first approach should be to reproduce the failure locally in the simplest way.

Here are some tips to do so:

- Open IntelliJ, and run the test.
- If it fails, great, we know it's reproducible locally.
- If it does not fail,
  [edit the run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html)
  that was just created (it will be named after the test), and make
  it [repeat until the test fails](https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html#tests)
  .

If you cannot reproduce it locally, the next step is to reproduce it in our CI environment.

#### Reproduce in CI

The first thing we'll do is create a branch so we can run everything in isolation.

##### Isolate test environment

```shell
git checkout -b 12983-flaky-test-issue-repro
git push --set-upstream origin 12983-flaky-test-issue-repro
```

This will also allow you to
run [the CI workflow](https://github.com/camunda/camunda/actions/workflows/zeebe-ci.yml) for that specific
branch as many times as you want. If you don't know how to do this, you can read up on
it [here](https://docs.github.com/en/actions/managing-workflow-runs/manually-running-a-workflow).

##### Run a single job

The next step is to narrow down the workflow to our failing test. This will make the feedback loop
shorter, and minimize resource usage during investigation.

> **Note**
> It can happen that a flaky test is only flaky when there is pressure on the system, so we
> cannot always fully narrow the workflow to a single test. You will have to experiment to find out
> which level is acceptable, i.e. how narrow you can make the scope while still reproducing the
> failure.

First, identify the job where the failing test is running. If that job is `Integration tests`, then
you should skip all other jobs in
the [zeebe-ci.yml workflow](https://github.com/camunda/camunda/blob/main/.github/workflows/zeebe-ci.yml). You can
do this easily by adding a `if: false` to every job definition except the one you want to run,
e.g. `integration-tests`.

Commit and push this to your branch, then using workflow dispatch, run the CI workflow multiple
times. If you can still reproduce it, then keep narrowing! If not, then revert the change, and give
up on narrowing.

##### Run a single test

Assuming you can still narrow the scope of the workflow, the next step is to have the job execute
only a single test. In
the [zeebe-ci.yml workflow](https://github.com/camunda/camunda/blob/main/.github/workflows/zeebe-ci.yml), under
the job you wish to execute (e.g. `integration-tests`), look for the step which is actually
executing the test, i.e. the one running `mvn verify`, or `mvn test`, etc. If the test is a unit
test (and as such run by surefire), then you can add `-Dtest=MyTestClass` (
see [the docs](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html)
for more ways of specifying tests). If it's failsafe, then you can add `-Dit.test=MyTestClass` (see
the [docs](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html) for
more). You may need to add `-DfailIfNoTests=false` if you end up building more than one module.

> NOTE: run the expected `mvn` command locally before pushing, just to make sure the command runs
> the test as expected.

Then as before, commit, push and run the CI workflow manually - check its execution to ensure it
behaves as expected. Try to reproduce the failure with this; if it works, great, that's a much
faster feedback loop. If it doesn't, then you unfortunately have to revert your changes.

##### Debugging

> NOTE: the end of this process is somewhat convoluted, so do not hesitate to suggest things which may
> simplify it.

Next is the fun part where it comes to debugging. You can use the `maven.surefire.debug` (
or `maven.failsafe.debug` for failsafe) property to attach a debugger to your test processes. By
default, this will start an agent at port 5005, and wait for a debugger to connect.

However, for most jobs, we run multiple forks, so you can imagine there would be port collisions!
Furthermore, we don't always want to wait for a debugger to connect before running test.

To circumvent this, you can specify the agent property through the `maven.surefire.debug`
or `maven.failsafe.debug` properties. To start a debugger agent without waiting your IDE to connect,
and using a random port, we can set the property
to: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n`.

To enable this for CI, find the job which runs your test, and under it, the step running the tests (
e.g. the command running `mvn verify` or `mvn test` or the likes). There, you can add the
property (`maven.debug.surefire` for `mvn test` or `maven.debug.failsafe` for `mvn verify`). For example:

```yaml
- name: Maven Test Build
  run: >
    mvn -B -T2 --no-snapshot-updates
    -D forkCount=5
    -D maven.javadoc.skip=true
    -D skipUTs -D skipChecks
    -D failsafe.rerunFailingTestsCount=3 -D flaky.test.reportDir=failsafe-reports
    -D maven.failsafe.debug=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n
    -P parallel-tests,extract-flaky-tests
    -pl '!qa/update-tests'
    verify
    | tee "${BUILD_OUTPUT_FILE_PATH}"
```

When the forks are started, they will print out the port which they use for the debugger, e.g.

```
[INFO] Listening for transport dt_socket at address: 60989
[INFO] Listening for transport dt_socket at address: 19382
[INFO] Listening for transport dt_socket at address: 32783
[INFO] Listening for transport dt_socket at address: 18293
[INFO] Listening for transport dt_socket at address: 53782
```

> This is rather inconvenient, and if someone finds a better way, please share! At the moment, this
> means you'll have to test each port until you find the right one.

To connect the debugger, you will need to port-forward the correct port to your local machine. The
first step here is to set up your k8s credentials for the cluster:

```shell
gcloud container clusters get-credentials zeebe-ci --zone europe-west1-b --project zeebe-io
```

Next, switch to the right context and namespace:

```shell
kubectx gke_zeebe-io_europe-west1-b_zeebe-ci
kubens default
```

We can now port-forward our debugger agent:

```shell
kubectl port-forward pod/actions-runner-16-n5tz8-nwtn2 5005:53782
```

> You can find the pod/runner name by going to your workflow run, selecting the job where the
> test is running, and looking at the first section of the logs, i.e. `Set up job`. The runner name
> is the pod name.

The above will forward connections to `localhost:5005` on your machine to the remote node at
port `53782`, where the agent is listening.

> Unfortunately, the IntelliJ `Attach to process` shortcut will not work here, as it will not
> discover the remote agent. You will have to create a new run configuration using
> the `Remote JVM Debug` template, targeting `localhost:5005` (which should be the default).

If you're running multiple tests/forks, and you need to figure out which port is the right one, I
suggest catching the failing assertion error, and adding a busy loop there, with a breakpoint in the
body. When connecting, it will stop there, and you will know you've connected to the right port. It
will also prevent your test from continuing, allowing you to inspect the state. For example:

```java
try {
  SslAssert.assertThat(socketAddress)
    .as("node %s is not secured correctly at address %s", nodeId, address)
    .isSecuredBy(certificate);
} catch (final AssertionError e) {
  final var watch = Stopwatch.createStarted();
  while (watch.elapsed().toNanos() < Duration.ofHours(1).toNanos()) {
    LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
  }
}
```

To cut down on reproduction times, you might sometimes want to run multiple workflow instances in
parallel. There I can recommend using [Webhook.site](https://webhook.site/), and having your test
ping that endpoint with some payload, so you can react to a test failure. For example:

```java
try {
  SslAssert.assertThat(socketAddress)
    .as("node %s is not secured correctly at address %s", nodeId, address)
    .isSecuredBy(certificate);
} catch (final AssertionError e) {
  final var webhook = URI.create(
      "https://webhook.site/<WEBHOOK UUID>?runnerId="
    + System.getenv("RUNNER_NAME")
    + "&pid="
    + ProcessHandle.current().pid());
  try {
    HttpClient.newHttpClient()
      .send(HttpRequest.newBuilder().GET().uri(webhook).build(), BodyHandlers.discarding());
  } catch (final Exception ignored) {
    // do nothing
  }
}
```

