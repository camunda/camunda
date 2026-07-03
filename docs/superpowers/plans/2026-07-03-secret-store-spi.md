# Secret Store SPI + File-Based Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a `SecretStore` SPI with a file-backed implementation that can be used by broker and gateway to resolve secret references.

**Architecture:** Two Maven sub-modules under a new top-level `secret-store/` directory: `secret-store-api` holds the pure-Java SPI (interface + value types, no Spring), `secret-store-file` holds the `.properties`-backed implementation. Both modules are registered in the root `pom.xml` and `parent/pom.xml`; broker/gateway wiring is out of scope.

**Tech Stack:** Java 21, JUnit 5, AssertJ, SLF4J (all already managed in `parent/pom.xml`), jspecify nullness annotations.

## Global Constraints

- All Java files must begin with the Camunda license header (run `./mvnw license:format` before every commit).
- Code style enforced by Spotless / Google Java Format — run `./mvnw spotless:apply` before every commit.
- Use `./mvnw` (never plain `mvn`). Add `-T1C` for parallel builds, `-q --batch-mode` to suppress noise.
- Build only the changed module(s): `./mvnw verify -pl <module> -DskipTests=false -Dquickly`.
- Use `// given`, `// when`, `// then` in test bodies. Prefix test methods with `should`.
- Prefer AssertJ assertions. No Hamcrest, no JUnit 4.
- No Spring dependencies in `secret-store-api` or `secret-store-file`.
- `resolve()` must never throw — store-level failures go into `SecretResolutionResult.Failed`.
- Never log resolved secret values — only ref names and counts are safe to log.
- All classes must be `@NullMarked`; nullable parameters annotated with `@Nullable`.

---

## File Map

**Created:**
- `secret-store/pom.xml` — parent aggregator, `packaging=pom`
- `secret-store/secret-store-api/pom.xml`
- `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/package-info.java`
- `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretRef.java`
- `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretErrorCode.java`
- `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretResolutionResult.java`
- `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretStore.java`
- `secret-store/secret-store-api/src/test/java/io/camunda/secretstore/SecretRefTest.java`
- `secret-store/secret-store-api/src/test/java/io/camunda/secretstore/SecretResolutionResultTest.java`
- `secret-store/secret-store-file/pom.xml`
- `secret-store/secret-store-file/src/main/java/io/camunda/secretstore/file/package-info.java`
- `secret-store/secret-store-file/src/main/java/io/camunda/secretstore/file/FileBasedSecretStore.java`
- `secret-store/secret-store-file/src/test/java/io/camunda/secretstore/file/FileBasedSecretStoreTest.java`

**Modified:**
- `pom.xml` (root) — add `<module>secret-store</module>`
- `parent/pom.xml` — add `camunda-secret-store-api` and `camunda-secret-store-file` to dependency management

---

## Task 1: Scaffold Maven modules

**Files:**
- Create: `secret-store/pom.xml`
- Create: `secret-store/secret-store-api/pom.xml`
- Create: `secret-store/secret-store-file/pom.xml`
- Modify: `pom.xml` (root, line 49 — after `<module>security</module>`)
- Modify: `parent/pom.xml` (after line 971 — after `camunda-security-validation` entry)

**Interfaces:**
- Produces: Maven modules compilable with `./mvnw install -pl secret-store -am -Dquickly -T1C`

- [ ] **Step 1: Create `secret-store/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
  ~ one or more contributor license agreements. See the NOTICE file distributed
  ~ with this work for additional information regarding copyright ownership.
  ~ Licensed under the Camunda License 1.0. You may not use this file
  ~ except in compliance with the Camunda License 1.0.
  -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.camunda</groupId>
    <artifactId>zeebe-parent</artifactId>
    <version>8.10.0-SNAPSHOT</version>
    <relativePath>../parent/pom.xml</relativePath>
  </parent>

  <artifactId>camunda-secret-store</artifactId>
  <packaging>pom</packaging>

  <name>Camunda Secret Store</name>

  <modules>
    <module>secret-store-api</module>
    <module>secret-store-file</module>
  </modules>

</project>
```

- [ ] **Step 2: Create `secret-store/secret-store-api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
  ~ one or more contributor license agreements. See the NOTICE file distributed
  ~ with this work for additional information regarding copyright ownership.
  ~ Licensed under the Camunda License 1.0. You may not use this file
  ~ except in compliance with the Camunda License 1.0.
  -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.camunda</groupId>
    <artifactId>camunda-secret-store</artifactId>
    <version>8.10.0-SNAPSHOT</version>
  </parent>

  <artifactId>camunda-secret-store-api</artifactId>

  <name>Camunda Secret Store API</name>

  <dependencies>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
    </dependency>

    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

</project>
```

- [ ] **Step 3: Create `secret-store/secret-store-file/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
  ~ one or more contributor license agreements. See the NOTICE file distributed
  ~ with this work for additional information regarding copyright ownership.
  ~ Licensed under the Camunda License 1.0. You may not use this file
  ~ except in compliance with the Camunda License 1.0.
  -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.camunda</groupId>
    <artifactId>camunda-secret-store</artifactId>
    <version>8.10.0-SNAPSHOT</version>
  </parent>

  <artifactId>camunda-secret-store-file</artifactId>

  <name>Camunda Secret Store File Implementation</name>

  <dependencies>
    <dependency>
      <groupId>io.camunda</groupId>
      <artifactId>camunda-secret-store-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
    </dependency>

    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

</project>
```

- [ ] **Step 4: Register `secret-store` in the root `pom.xml`**

In `pom.xml`, add after the `<module>security</module>` line (line 49):

```xml
    <module>secret-store</module>
```

- [ ] **Step 5: Add dependency management entries to `parent/pom.xml`**

In `parent/pom.xml`, add after the `camunda-security-validation` block (after line 971, before the `<!-- Camunda Process Testing modules -->` comment):

```xml
      <!-- Camunda Secret Store modules -->

      <dependency>
        <groupId>io.camunda</groupId>
        <artifactId>camunda-secret-store-api</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.camunda</groupId>
        <artifactId>camunda-secret-store-file</artifactId>
        <version>${project.version}</version>
      </dependency>
```

- [ ] **Step 6: Verify the modules compile**

```bash
./mvnw install -pl secret-store -am -Dquickly -T1C -q --batch-mode 2>&1 | grep -E "BUILD|ERROR"
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add secret-store/pom.xml secret-store/secret-store-api/pom.xml secret-store/secret-store-file/pom.xml pom.xml parent/pom.xml
git commit -m "feat: scaffold secret-store Maven modules"
```

---

## Task 2: Implement API types

**Files:**
- Create: `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/package-info.java`
- Create: `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretRef.java`
- Create: `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretErrorCode.java`
- Create: `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretResolutionResult.java`
- Create: `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretStore.java`
- Create: `secret-store/secret-store-api/src/test/java/io/camunda/secretstore/SecretRefTest.java`
- Create: `secret-store/secret-store-api/src/test/java/io/camunda/secretstore/SecretResolutionResultTest.java`

**Interfaces:**
- Produces:
  - `SecretRef(String name)` — record, validates non-null/non-blank
  - `SecretErrorCode` — enum: `NOT_FOUND`, `ACCESS_DENIED`, `STORE_UNAVAILABLE`, `INVALID_REF`
  - `SecretResolutionResult` — sealed: `Resolved(String value)`, `Failed(SecretErrorCode, String, @Nullable Throwable)`
  - `SecretStore` — interface: `resolve(Set<SecretRef>)`, `list()`, default no-op `close()`

- [ ] **Step 1: Write the failing `SecretRefTest`**

Create `secret-store/secret-store-api/src/test/java/io/camunda/secretstore/SecretRefTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class SecretRefTest {

  @Test
  void shouldCreateWithName() {
    // given / when
    final var ref = new SecretRef("my-secret");

    // then
    assertThat(ref.name()).isEqualTo("my-secret");
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> new SecretRef(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> new SecretRef("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldRejectEmptyName() {
    assertThatThrownBy(() -> new SecretRef(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank");
  }

  @Test
  void shouldSupportEqualityAndHashCode() {
    // given
    final var ref1 = new SecretRef("my-secret");
    final var ref2 = new SecretRef("my-secret");

    // then
    assertThat(ref1).isEqualTo(ref2);
    assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
  }

  @Test
  void shouldBeUsableAsMapKey() {
    // given
    final var map = new HashMap<SecretRef, String>();
    final var ref = new SecretRef("my-secret");
    map.put(ref, "value");

    // when / then
    assertThat(map.get(new SecretRef("my-secret"))).isEqualTo("value");
  }
}
```

- [ ] **Step 2: Verify test fails to compile**

```bash
./mvnw test-compile -pl secret-store/secret-store-api -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|ERROR|cannot find"
```

Expected: compile error — `SecretRef` does not exist.

- [ ] **Step 3: Create `SecretRef.java`**

Create `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretRef.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Objects;

public record SecretRef(String name) {
  public SecretRef {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
```

- [ ] **Step 4: Run `SecretRefTest` and verify it passes**

```bash
./mvnw verify -pl secret-store/secret-store-api -Dtest=SecretRefTest -DskipTests=false -DskipITs -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|Tests run|FAIL|ERROR"
```

Expected: `BUILD SUCCESS`, `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Write the failing `SecretResolutionResultTest`**

Create `secret-store/secret-store-api/src/test/java/io/camunda/secretstore/SecretResolutionResultTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretResolutionResultTest {

  @Test
  void shouldCarryResolvedValue() {
    // given / when
    final var result = new SecretResolutionResult.Resolved("hunter2");

    // then
    assertThat(result.value()).isEqualTo("hunter2");
  }

  @Test
  void shouldMaskResolvedToString() {
    // given
    final var result = new SecretResolutionResult.Resolved("hunter2");

    // when
    final var str = result.toString();

    // then — secret value must not appear in log output
    assertThat(str).doesNotContain("hunter2");
    assertThat(str).contains("***");
  }

  @Test
  void shouldCarryFailedCodeMessageAndCause() {
    // given
    final var cause = new RuntimeException("connection refused");

    // when
    final var result =
        new SecretResolutionResult.Failed(SecretErrorCode.STORE_UNAVAILABLE, "store is down", cause);

    // then
    assertThat(result.code()).isEqualTo(SecretErrorCode.STORE_UNAVAILABLE);
    assertThat(result.message()).isEqualTo("store is down");
    assertThat(result.cause()).isSameAs(cause);
  }

  @Test
  void shouldAcceptNullCauseInFailed() {
    // given / when
    final var result =
        new SecretResolutionResult.Failed(SecretErrorCode.NOT_FOUND, "missing", null);

    // then
    assertThat(result.cause()).isNull();
  }

  @Test
  void shouldSupportExhaustivePatternSwitch() {
    // given
    final SecretResolutionResult result = new SecretResolutionResult.Resolved("val");

    // when
    final String output =
        switch (result) {
          case SecretResolutionResult.Resolved r -> "resolved:" + r.value();
          case SecretResolutionResult.Failed f -> "failed:" + f.code();
        };

    // then
    assertThat(output).isEqualTo("resolved:val");
  }
}
```

- [ ] **Step 6: Verify test fails to compile**

```bash
./mvnw test-compile -pl secret-store/secret-store-api -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|ERROR|cannot find"
```

Expected: compile error — `SecretResolutionResult` and `SecretErrorCode` do not exist.

- [ ] **Step 7: Create `SecretErrorCode.java`**

Create `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretErrorCode.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

public enum SecretErrorCode {
  NOT_FOUND,
  ACCESS_DENIED,
  STORE_UNAVAILABLE,
  INVALID_REF
}
```

- [ ] **Step 8: Create `SecretResolutionResult.java`**

Create `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretResolutionResult.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface SecretResolutionResult
    permits SecretResolutionResult.Resolved, SecretResolutionResult.Failed {

  record Resolved(String value) implements SecretResolutionResult {
    @Override
    public String toString() {
      return "Resolved[value=***]";
    }
  }

  record Failed(SecretErrorCode code, String message, @Nullable Throwable cause)
      implements SecretResolutionResult {}
}
```

- [ ] **Step 9: Run `SecretResolutionResultTest` and verify it passes**

```bash
./mvnw verify -pl secret-store/secret-store-api -Dtest=SecretResolutionResultTest -DskipTests=false -DskipITs -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|Tests run|FAIL|ERROR"
```

Expected: `BUILD SUCCESS`, `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 10: Create `SecretStore.java`**

Create `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/SecretStore.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface SecretStore extends AutoCloseable {

  /**
   * Resolves a set of secret references in a single call.
   *
   * <p>Returns a result for every ref in the input set. Never throws — store-level failures
   * (unreachable backend, missing file, etc.) are reported as {@link SecretResolutionResult.Failed}
   * with code {@link SecretErrorCode#STORE_UNAVAILABLE}.
   *
   * <p>Implementations must be thread-safe.
   */
  Map<SecretRef, SecretResolutionResult> resolve(Set<SecretRef> refs);

  /**
   * Lists all secret references known to this store. May throw {@link
   * java.io.UncheckedIOException} on store-level failures.
   */
  Collection<SecretRef> list();

  @Override
  default void close() {}
}
```

- [ ] **Step 11: Create `package-info.java`**

Create `secret-store/secret-store-api/src/main/java/io/camunda/secretstore/package-info.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
@NullMarked
package io.camunda.secretstore;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 12: Run full module tests**

```bash
./mvnw verify -pl secret-store/secret-store-api -DskipTests=false -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|Tests run|FAIL|ERROR"
```

Expected: `BUILD SUCCESS`, `Tests run: 11, Failures: 0, Errors: 0`

- [ ] **Step 13: Format and commit**

```bash
./mvnw license:format spotless:apply -pl secret-store/secret-store-api -T1C -q --batch-mode 2>&1 | grep -E "BUILD|ERROR"
git add secret-store/secret-store-api/
git commit -m "feat: add SecretStore SPI and value types"
```

---

## Task 3: Implement `FileBasedSecretStore`

**Files:**
- Create: `secret-store/secret-store-file/src/main/java/io/camunda/secretstore/file/package-info.java`
- Create: `secret-store/secret-store-file/src/main/java/io/camunda/secretstore/file/FileBasedSecretStore.java`
- Create: `secret-store/secret-store-file/src/test/java/io/camunda/secretstore/file/FileBasedSecretStoreTest.java`

**Interfaces:**
- Consumes: `SecretStore`, `SecretRef`, `SecretResolutionResult`, `SecretErrorCode` from `camunda-secret-store-api`
- Produces: `FileBasedSecretStore(Path filePath)` — reads `.properties` file (UTF-8), lazy per call

- [ ] **Step 1: Write the failing `FileBasedSecretStoreTest`**

Create `secret-store/secret-store-file/src/test/java/io/camunda/secretstore/file/FileBasedSecretStoreTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretRef;
import io.camunda.secretstore.SecretResolutionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBasedSecretStoreTest {

  @TempDir Path tempDir;

  @Test
  void shouldResolveKnownSecret() throws IOException {
    // given
    final var file = writeProperties("db-password=s3cr3t");
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new SecretRef("db-password")));

    // then
    assertThat(result.get(new SecretRef("db-password")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldReturnFailedForUnknownRef() throws IOException {
    // given
    final var file = writeProperties("other=value");
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new SecretRef("missing")));

    // then
    final var entry = result.get(new SecretRef("missing"));
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldResolveMultipleRefsInOneBatch() throws IOException {
    // given
    final var file = writeProperties("a=1\nb=2");
    final var store = new FileBasedSecretStore(file);

    // when
    final var refs = Set.of(new SecretRef("a"), new SecretRef("b"), new SecretRef("missing"));
    final var result = store.resolve(refs);

    // then — result map contains an entry for every requested ref
    assertThat(result)
        .containsKeys(new SecretRef("a"), new SecretRef("b"), new SecretRef("missing"));
    assertThat(result.get(new SecretRef("a"))).isInstanceOf(SecretResolutionResult.Resolved.class);
    assertThat(result.get(new SecretRef("b"))).isInstanceOf(SecretResolutionResult.Resolved.class);
    assertThat(result.get(new SecretRef("missing")))
        .isInstanceOf(SecretResolutionResult.Failed.class);
  }

  @Test
  void shouldListAllSecrets() throws IOException {
    // given
    final var file = writeProperties("a=1\nb=2\nc=3");
    final var store = new FileBasedSecretStore(file);

    // when
    final var list = store.list();

    // then
    assertThat(list)
        .containsExactlyInAnyOrder(new SecretRef("a"), new SecretRef("b"), new SecretRef("c"));
  }

  @Test
  void shouldPickUpRotatedValue() throws IOException {
    // given
    final var file = writeProperties("api-key=old-value");
    final var store = new FileBasedSecretStore(file);

    // when — first resolve returns old value
    assertThat(store.resolve(Set.of(new SecretRef("api-key"))).get(new SecretRef("api-key")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("old-value");

    // rotate the file
    Files.writeString(file, "api-key=new-value", StandardCharsets.UTF_8);

    // then — second resolve picks up the new value without restart
    assertThat(store.resolve(Set.of(new SecretRef("api-key"))).get(new SecretRef("api-key")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("new-value");
  }

  @Test
  void shouldReturnStoreUnavailableWhenFileMissing() {
    // given
    final var store = new FileBasedSecretStore(tempDir.resolve("nonexistent.properties"));

    // when — must not throw
    final var result = store.resolve(Set.of(new SecretRef("any"), new SecretRef("other")));

    // then — every ref gets STORE_UNAVAILABLE, no exception propagated
    assertThat(result.values())
        .allSatisfy(
            r -> {
              assertThat(r).isInstanceOf(SecretResolutionResult.Failed.class);
              assertThat(((SecretResolutionResult.Failed) r).code())
                  .isEqualTo(SecretErrorCode.STORE_UNAVAILABLE);
            });
  }

  @Test
  void shouldHandleUtf8Values() throws IOException {
    // given — non-Latin-1 characters that would be mangled by ISO-8859-1
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "token=café-中文", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new SecretRef("token")));

    // then
    assertThat(result.get(new SecretRef("token")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("café-中文");
  }

  @Test
  void shouldHandleValuesWithSpecialPropertiesChars() throws IOException {
    // given — values containing characters that are special in .properties format (escaped)
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "key1=val\\=ue\nkey2=has\\:colon\n", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new SecretRef("key1"), new SecretRef("key2")));

    // then
    assertThat(((SecretResolutionResult.Resolved) result.get(new SecretRef("key1"))).value())
        .isEqualTo("val=ue");
    assertThat(((SecretResolutionResult.Resolved) result.get(new SecretRef("key2"))).value())
        .isEqualTo("has:colon");
  }

  @Test
  void shouldBeThreadSafe() throws IOException, InterruptedException {
    // given
    final var file = writeProperties("secret=value");
    final var store = new FileBasedSecretStore(file);
    final int threadCount = 10;
    final int callsPerThread = 100;
    final var latch = new CountDownLatch(1);
    final var errors = new AtomicInteger(0);
    final var executor = Executors.newFixedThreadPool(threadCount);

    // when — all threads released simultaneously via the latch
    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              latch.await();
              for (int j = 0; j < callsPerThread; j++) {
                final var result = store.resolve(Set.of(new SecretRef("secret")));
                if (!(result.get(new SecretRef("secret"))
                    instanceof SecretResolutionResult.Resolved)) {
                  errors.incrementAndGet();
                }
                store.list();
              }
            } catch (final Exception e) {
              errors.incrementAndGet();
            }
          });
    }
    latch.countDown();
    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    // then — zero errors across all concurrent calls
    assertThat(errors.get()).isZero();
  }

  private Path writeProperties(final String content) throws IOException {
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }
}
```

- [ ] **Step 2: Verify test fails to compile**

```bash
./mvnw test-compile -pl secret-store/secret-store-file -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|ERROR|cannot find"
```

Expected: compile error — `FileBasedSecretStore` does not exist.

- [ ] **Step 3: Create `FileBasedSecretStore.java`**

Create `secret-store/secret-store-file/src/main/java/io/camunda/secretstore/file/FileBasedSecretStore.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.SecretErrorCode.STORE_UNAVAILABLE;
import static java.util.stream.Collectors.toMap;

import io.camunda.secretstore.SecretRef;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class FileBasedSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileBasedSecretStore.class);

  private final Path filePath;

  public FileBasedSecretStore(final Path filePath) {
    this.filePath = filePath;
  }

  @Override
  public Map<SecretRef, SecretResolutionResult> resolve(final Set<SecretRef> refs) {
    final Properties props;
    try {
      props = loadProperties();
    } catch (final UncheckedIOException e) {
      LOG.warn("Failed to load secrets file '{}': {}", filePath, e.getCause().getMessage());
      final var msg = "Failed to load secrets file: " + e.getCause().getMessage();
      return refs.stream()
          .collect(
              toMap(
                  ref -> ref,
                  ref ->
                      new SecretResolutionResult.Failed(STORE_UNAVAILABLE, msg, e.getCause())));
    }
    // Never log resolved values — only ref names and counts are safe to log
    LOG.debug("Resolving {} secret refs from '{}'", refs.size(), filePath);
    return refs.stream()
        .collect(
            toMap(
                ref -> ref,
                ref -> {
                  final var value = props.getProperty(ref.name());
                  return value != null
                      ? new SecretResolutionResult.Resolved(value)
                      : new SecretResolutionResult.Failed(
                          NOT_FOUND, "Secret not found: " + ref.name(), null);
                }));
  }

  @Override
  public Collection<SecretRef> list() {
    final var props = loadProperties();
    LOG.debug("Listing {} secrets from '{}'", props.size(), filePath);
    return props.stringPropertyNames().stream().map(SecretRef::new).toList();
  }

  private Properties loadProperties() {
    final var props = new Properties();
    try (final var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return props;
  }
}
```

- [ ] **Step 4: Create `package-info.java`**

Create `secret-store/secret-store-file/src/main/java/io/camunda/secretstore/file/package-info.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
@NullMarked
package io.camunda.secretstore.file;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 5: Run all tests in `secret-store-file` and verify they pass**

```bash
./mvnw verify -pl secret-store/secret-store-file -DskipTests=false -Dquickly -q --batch-mode 2>&1 | grep -E "BUILD|Tests run|FAIL|ERROR"
```

Expected: `BUILD SUCCESS`, `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 6: Run full repo compile to check nothing is broken**

```bash
./mvnw install -Dquickly -T1C -q --batch-mode 2>&1 | grep -E "BUILD|ERROR"
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Format and commit**

```bash
./mvnw license:format spotless:apply -pl secret-store -T1C -q --batch-mode 2>&1 | grep -E "BUILD|ERROR"
git add secret-store/secret-store-file/
git commit -m "feat: add FileBasedSecretStore implementation"
```
