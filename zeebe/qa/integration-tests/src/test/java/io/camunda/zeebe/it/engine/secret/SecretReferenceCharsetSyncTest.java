/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.SecretServices;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Holds the two definitions of the secret reference name charset together.
 *
 * <p>{@link SecretReference#REFERENCE_PATTERN} (the engine's raw-text scanners) and {@link
 * SecretServices#REFERENCE_NAME_PATTERN} (the {@code /v2/secrets} endpoints) describe the same
 * thing in two modules, because {@code service} cannot depend on {@code zeebe/engine}. They have to
 * agree: a name one accepts and the other rejects is storable and listable but not resolvable,
 * which is exactly the failure of <a href="https://github.com/camunda/camunda/issues/60364">
 * #60364</a>. Nothing but this test fails when only one of them is edited.
 *
 * <p>This module is the only one with both artifacts on its classpath, which is why a unit test
 * lives here.
 */
final class SecretReferenceCharsetSyncTest {

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        // inside the charset
        "token",
        "db-password",
        "MY_SECRET_2",
        "_underscore",
        "-lead",
        "trail-",
        "9",
        // outside it — a dot is a further FEEL segment, the rest are no FEEL identifier at all
        "tls.crt",
        "a b",
        "a*b",
        "a%b",
        "a/b",
        // outside it, and detected by the FEEL AST walk regardless: both patterns are ASCII-only
        // while SecretReference.parse applies no charset check at all
        "tokén",
        "api$key",
      })
  void shouldAcceptTheSameNamesInBothModules(final String name) {
    // when the same name is offered to the engine's raw-text pattern and to the endpoints'
    final var engineAccepts =
        SecretReference.REFERENCE_PATTERN.matcher(SecretReference.PREFIX + name).matches();
    final var serviceAccepts = SecretServices.REFERENCE_NAME_PATTERN.matcher(name).matches();

    // then they agree
    assertThat(serviceAccepts)
        .as(
            "the endpoints and the engine detector disagree on '%s': widening or narrowing one "
                + "charset requires the same edit to the other",
            name)
        .isEqualTo(engineAccepts);
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", " ", "."})
  void shouldRejectANameThatIsNotOneInBothModules(final String name) {
    // given a name that is empty or is a single out-of-charset character, which neither module may
    // read as a reference name — the empty case above all, since a bare 'camunda.secrets.' prefix
    // must not scan as a reference to an unnamed secret
    // then
    assertThat(SecretReference.REFERENCE_PATTERN.matcher(SecretReference.PREFIX + name).matches())
        .isFalse();
    assertThat(SecretServices.REFERENCE_NAME_PATTERN.matcher(name).matches()).isFalse();
  }
}
