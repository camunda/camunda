# Gradle parity TODO from the rebase

## Audit scope

- Baseline: `292f007fc896` (the old `origin/cs/gradle` merge-base).
- Audited the 850 Maven commits from that baseline through `origin/main`; together with the
  five Gradle commits this is the 855-commit rebase range.
- Found 71 commits touching a `pom.xml`. Version-only dependency bumps were intentionally
  excluded, as requested.
- The items below describe the **final Maven state**, not transient changes that were later
  reverted. Related add/remove commits are grouped where that makes the port actionable.
- The current Gradle settings now register 146 projects, including the ported
  `secret-store/secret-store-aws` module.

## Port these Maven changes

### Module graph and AWS secret-store implementation

- [x] **`c50eb6d2b7a6`, `69e2de41677e`, `5161c65a019e`** — add the AWS secret-store module.
  - Add `:camunda-secret-store-aws` to `settings.gradle.kts`, mapped to
    `secret-store/secret-store-aws`.
  - Add `secret-store/secret-store-aws/build.gradle.kts`.
  - Mirror the final `secret-store/secret-store-aws/pom.xml`: secret-store API, JSpecify, SLF4J,
    Jackson, AWS Secrets Manager/SDK dependencies, and the JUnit/AssertJ/Mockito/Testcontainers
    test dependencies.
  - Add missing version-catalog aliases for the AWS `secretsmanager`, `retries`, and
    `retries-spi` artifacts. They should use the existing AWS SDK BOM/version rather than free
    versions.
  - Add the AWS module to `dist/build.gradle.kts` so the shipped distribution contains it.

### Dependency graph and scope changes

- [x] **`425c525f9321`** — add `:zeebe-restore` as an implementation dependency of
  `zeebe/broker/build.gradle.kts`.
- [x] **`6373a05b1fc0`** — add `:camunda-secret-store-api` to both
  `zeebe/broker/build.gradle.kts` and `zeebe/engine/build.gradle.kts`.
- [x] **`0fe2e5e39bf6`, `b8823c056f7c`, `e8ee613ec959`, `629b5000b22c`** — finish the direct
  dependencies of `zeebe/restore/build.gradle.kts`:
  - Jackson databind and Jackson core as main dependencies;
  - `junit-jupiter-params` as a test dependency.
  - Micrometer, JSpecify, and `:zeebe-cluster-config` are represented in the Gradle file.
  - Removed the stale pre-existing `:zeebe-broker` dependency from `zeebe/restore` to match Maven
    and avoid a broker/restore circular dependency after adding restore to the broker.
- [x] **`0fe2e5e39bf6`, `5161c65a019e`** — add `:zeebe-restore` and
  `:camunda-secret-store-aws` to `dist/build.gradle.kts`.
- [x] **`67a57dc0421c`** — in `zeebe/engine/build.gradle.kts`, add
  `jackson-dataformat-msgpack` and move `msgpack-core` from `testImplementation` to
  `implementation`. The Maven dependency-analysis suppression is not itself needed in Gradle.
- [x] **`db11ef4881cf`** — promote `:camunda-cluster` in `zeebe/gateway/build.gradle.kts` from
  `testImplementation` to the main implementation configuration.
- [x] **`ac801e72b3eb`** — remove `testImplementation(project(":zeebe-restore"))` from
  `zeebe/qa/integration-tests/build.gradle.kts`; the Maven test dependency was removed.
- [x] **`01d769944de4`, `82896056619`, `6c5d97216f`, `62c5063eceac`, `ce095f4b8923`** — port
  the final `search/search-client-connect` dependency state: add AWS `sts` and `aws-core` to
  `search/search-client-connect/build.gradle.kts`. Do not port the intermediate removal or the
  Maven dependency-analysis ignore block; the final POM contains both dependencies.
- [x] **`7eefa6f9539`, `de9c0ba179d9`** — add Spring Boot to `spring-utils/build.gradle.kts` as
  `compileOnly` plus `testImplementation`, matching Maven `provided` scope. It must not leak to
  consumers of `spring-utils`.
- [x] **`8f95ed61f097`** — add `implementation(project(":camunda-spring-utils"))` to
  `configuration/build.gradle.kts`.
- [x] **`44ff75a0a099`** — add the Camunda Security Library core and Spring Boot starter to
  `optimize/backend/build.gradle.kts`.
- [x] **`a79a58772a84`, `ae5825eaa87`** — complete the direct dependencies of
  `operate/data-generator/build.gradle.kts`: `:camunda-service`, Jackson core,
  `:zeebe-protocol-impl`, `:zeebe-protocol`, and the test Mockito/AssertJ/JUnit dependencies.
  `:camunda-schema-manager` is now present as the direct dependency introduced by the later Maven
  change.
- [x] **`9c3048a07234`, `ad27ed9bb956`** — remove the dependencies deleted from the final Operate
  POMs. In particular, remove the stale Elasticsearch/OpenSearch client declarations from
  `operate/common/build.gradle.kts` (and `:zeebe-util` if it is still present), and remove the
  AWS STS declaration from `operate/webapp/build.gradle.kts`. Preserve dependencies that remain
  in the final POM, such as HTTP Core 5 where applicable.
- [x] **`ef8ebebe8906`, `bf36c6c3f87`** — add ASM as a test dependency to
  `qa/archunit-tests/build.gradle.kts`; do not re-add AssertJ, which the later refactor removed.
  Add the corresponding `org.ow2.asm:asm` catalog alias if needed.
- [x] **`86145f999513`** — add `nimbus-jose-jwt` as a test dependency of
  `qa/acceptance-tests/build.gradle.kts`.
- [x] **`6199898b568e`, `76539ddfeeb`** — add the final
  `ClusterVariableResultBaseMetadataValue -> Object` type mapping to the OpenAPI generation
  configuration in `clients/java/build.gradle.kts`.

### Acceptance-test profile parity

- [x] **`7117a204e406`, `8de8303b3be7`, `1a1d47aeede7`** — implement the Gradle
  equivalent of the final Maven physical-tenant acceptance profiles. The Gradle test setup needs
  the final identity/history tag selection, exclusions, and
  `camunda.test.preferred.extension=multi-db` system property if those CI shards are run in
  Gradle mode. Preserve the later removals: the tests disabled by annotation and the MCP
  exclusions must not be restored.

## Validation

- `./gradlew classes --parallel --configuration-cache` passes for the full Gradle project graph.
- Targeted Gradle compilation and tests pass for the affected modules; the physical-tenant profile
  tasks also configure successfully in dry-run mode.
- The full Maven `./mvnw install -Dquickly -T1C` baseline and final verification pass.
- `SystemContextTest.shouldThrowExceptionWhenS3IsNotConfigured` fails consistently in both Gradle
  and Maven with the same assertion, so it is pre-existing and unrelated to this Gradle parity
  port.

## Intentionally not ported / verify only

These POM changes are Maven-release or Maven-plugin behavior that the current Gradle parity
scope explicitly does not implement:

- **`ea278d7793c6`** — `flatten-maven-plugin` configuration for publishing self-contained POMs.
- **`cf6e9a64da98`** — Maven source-jar attachment configuration for the Spring Boot 3 starter.
- **`bfb7db9665ef`, `70c038c28e8c`** — empty javadoc artifact handling for the empty AWS module.
- **`c3e1c3a98649`** — Maven dependency-analysis configuration; the version bump is ignored.

The Gradle skill currently lists flattened POMs, source/javadoc jars, and Maven dependency
analysis among known parity gaps. Revisit these only if Gradle publication/release parity becomes
part of the acceptance criteria.

Version-only commits (AWS SDK, CSL, Feel, Testcontainers, JSON-schema, and LangChain versions)
were audited and intentionally omitted. The Gradle catalog continues to source versions from
Maven, so those updates should flow through the existing `pomVersion(...)` lookups.
