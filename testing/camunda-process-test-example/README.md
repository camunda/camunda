# Camunda-Process-Test-Example

An example project to demonstrate the usage of Camunda Process Test (Spring) for a demo Spring Boot process application.

The test cases are located in the package [src/test/java/io/camunda](src/test/java/io/camunda). You can run the tests locally in your IDE with a JUnit 5 test runner.

Running these tests also generates a Camunda Process Test coverage report, which is handy for
previewing the [coverage frontend](../camunda-process-test-coverage) with real data.

Read more about the library and how to use it in our documentation: https://docs.camunda.io/docs/apis-tools/testing/getting-started/.

## Isolated recording example (alpha)

`isolated-order.bpmn` and the test cases in
[src/test/resources/test-cases-isolated](src/test/resources/test-cases-isolated) exercise the two
recording switches on `CREATE_PROCESS_INSTANCE`:

- `reserveJobs` — every job the instance creates is served to no job worker, so the test drives it
  by key. A reserved job is hidden from CPT's own `mockJobWorker` too, so mock those jobs with
  `COMPLETE_JOB` / `THROW_BPMN_ERROR_FROM_JOB` rather than `MOCK_JOB_WORKER_*`, or
  release them with `RELEASE_JOB`.
- `stubCallActivities` — each call activity activates without starting the process it calls and
  waits on a job instead. Drive it by the call activity's element with `STUB_CALL_ACTIVITY_COMPLETE`
  to stand in for the called process, `STUB_CALL_ACTIVITY_THROW_ERROR` to make it go wrong, or
  `RUN_CALLED_PROCESS` to run the called process for real. The switch is inherited, so a call
  activity nested at any depth is stubbed too.

These call-activity instructions select the element, not the job, so they read as what they do to
the call activity. Under the hood each drives the job the stub waits on — a job that shares its
element ID with the element's listener jobs and is hidden by reservation too — but you never name
that job. The call activity is identified by its `elementId`.

The processes are `isolated-order` → `shipping` → `notification`, so the nesting and the error
boundary event on the call activity are both covered.

`IsolatedOrderJsonTest` runs them, and is **disabled**: both switches are alpha and unmerged, while
the default runtime pulls `camunda/camunda:SNAPSHOT`, which is built from `main`. Point it at an
engine that has them in one of two ways.

**Managed, with an image built from the branch:**

Build the distribution and containerize it (see the
[root CONTRIBUTING](../../CONTRIBUTING.md) for the base-image options):

```bash
# from the repository root
./mvnw clean install -DskipTests -DskipChecks
docker build \
  --tag camunda/camunda:isolated-jobs \
  --build-arg DISTBALL='dist/target/camunda-zeebe*.tar.gz' \
  --build-arg BASE='public' \
  --target app \
  --file ./camunda.Dockerfile \
  .
```

```properties
# src/test/resources/application.yml, or as a @SpringBootTest property
camunda.process-test.camunda-docker-image-name=camunda/camunda
camunda.process-test.camunda-docker-image-version=isolated-jobs
```

**Remote, against a cluster you started yourself** — for example `c8run` or a broker started from
this branch:

```properties
camunda.process-test.runtime-mode=remote
camunda.process-test.remote.client.rest-address=http://localhost:8080
camunda.process-test.remote.client.grpc-address=http://localhost:26500
```

Remote mode runs against a live cluster, so read the cleanup section of the testing docs first:
CPT purges the cluster after each test by default.

Then drop the `@Disabled` annotation and run the class.

## Contributing

See the [testing contribution guide](../CONTRIBUTING.md).
