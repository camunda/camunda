# Camunda-Process-Test-Example

An example project to demonstrate the usage of Camunda Process Test (Spring) for a demo Spring Boot process application.

The test cases are located in the package [src/test/java/io/camunda](src/test/java/io/camunda). You can run the tests locally in your IDE with a JUnit 5 test runner.

Running these tests also generates a Camunda Process Test coverage report, which is handy for
previewing the [coverage frontend](../camunda-process-test-coverage) with real data.

Read more about the library and how to use it in our documentation: https://docs.camunda.io/docs/apis-tools/testing/getting-started/.

## Isolated recording example (alpha)

`isolated-order.bpmn` and the test cases in
[src/test/resources/test-cases-isolated](src/test/resources/test-cases-isolated) exercise the
recording switches, which ride on runtime instructions of `CREATE_PROCESS_INSTANCE`:

- `RESERVE_JOBS` — every job of a listed job type is served to no job worker, so the test drives it
  by key. Jobs of any other type go to the workers as usual, so to let a job run for real, leave its
  type off the list. A reserved job is hidden from CPT's own `mockJobWorker` too, so mock those jobs
  with `COMPLETE_JOB` / `THROW_BPMN_ERROR_FROM_JOB` rather than `MOCK_JOB_WORKER_*`. The instruction
  carries a `jobReservationToken`, and so does every job instruction that acts on a reserved job.
  Write the placeholder `${jobReservationToken}` for it: the runner substitutes a fresh token on each
  run, so no real token is ever saved with a test case.
- `STUB_CALL_ACTIVITIES` — each call activity calling a listed process id activates without starting
  the process it calls and waits on a stand-in job instead. Complete that job with `COMPLETE_JOB` to stand in for the called
  process, or `THROW_BPMN_ERROR_FROM_JOB` to make it go wrong — selecting it by
  `jobKind: CALL_ACTIVITY_STUB` and the call activity's `elementId` — or use `RUN_CALLED_PROCESS` to
  run the called process for real. The switch is inherited, so a call activity nested at any depth is
  stubbed too.

`COMPLETE_JOB` and `THROW_BPMN_ERROR_FROM_JOB` select the stand-in job directly, by its
`CALL_ACTIVITY_STUB` kind and the call activity's `elementId` — the job the stub waits on. A stub job
is hidden from workers by its kind, independently of `RESERVE_JOBS`, so it needs no token. `RUN_CALLED_PROCESS` selects the call activity by its `elementId`.

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
