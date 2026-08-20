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
import org.junit.jupiter.api.Test;
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
 * <p>The two sets below are golden rather than a mutual comparison: each name is asserted against
 * the verdict it must get, not merely against what the other pattern says about it. Asserting only
 * that the two agree would pass a wrong edit applied to both — dropping the dash from each pattern
 * reintroduces #60364 while leaving them perfectly in sync.
 *
 * <p>This module is the only one with both artifacts on its classpath, which is why a unit test
 * lives here.
 */
final class SecretReferenceCharsetSyncTest {

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        "token",
        "db-password",
        "MY_SECRET_2",
        "_underscore",
        // a dash anywhere in the name, including runs of them and a name that is nothing else: all
        // legal store keys, and neither pattern is anchored to keep one out
        "a--b",
        "-lead",
        "trail-",
        "-",
        "--",
        "9",
      })
  void shouldAcceptTheSameNamesInBothModules(final String name) {
    assertAccepted(name);
  }

  @Test
  void shouldAcceptANameFillingTheWholeReferenceLength() {
    // given the longest name a reference of MAX_REFERENCE_LENGTH can carry. Derived from the
    // constants rather than written out, so the case follows a change to either.
    final var name =
        "a".repeat(SecretServices.MAX_REFERENCE_LENGTH - SecretReference.PREFIX.length());

    // then both charsets take it: they say what a name is made of and nothing about how long it may
    // be, so neither may bound the length by accident. The 256 cap is MAX_REFERENCE_LENGTH's own,
    // pinned at and one past the boundary by SecretServicesTest.
    assertAccepted(name);
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        // a dot is a further FEEL segment, the rest are no FEEL identifier at all
        "tls.crt",
        "a b",
        "a*b",
        "a%b",
        "a/b",
        // out of charset, and detected by the FEEL AST walk regardless: both patterns are
        // ASCII-only
        // while SecretReference.parse applies no charset check at all
        "tokén",
        "api$key",
        // no name at all. The empty case above all: a bare 'camunda.secrets.' prefix must not scan
        // as a reference to an unnamed secret.
        "",
        " ",
        ".",
      })
  void shouldRejectTheSameNamesInBothModules(final String name) {
    // when the same name is offered to the engine's raw-text pattern and to the endpoints'
    // then neither reads it as a reference name
    assertThat(engineAccepts(name))
        .as("the engine detector accepts '%s', which the endpoints reject", name)
        .isFalse();
    assertThat(serviceAccepts(name))
        .as("the endpoints accept '%s', which the engine detector rejects", name)
        .isFalse();
  }

  private static void assertAccepted(final String name) {
    // when the same name is offered to the engine's raw-text pattern and to the endpoints'
    // then both read it as a reference name
    assertThat(engineAccepts(name))
        .as(
            "the engine detector rejects '%s': narrowing this charset requires the same edit to "
                + "SecretServices.REFERENCE_NAME_PATTERN, and leaves the name unresolvable",
            name)
        .isTrue();
    assertThat(serviceAccepts(name))
        .as(
            "the endpoints reject '%s': narrowing this charset requires the same edit to "
                + "SecretReference.REFERENCE_PATTERN, and makes the name storable but not "
                + "resolvable",
            name)
        .isTrue();
  }

  private static boolean engineAccepts(final String name) {
    return SecretReference.REFERENCE_PATTERN.matcher(SecretReference.PREFIX + name).matches();
  }

  private static boolean serviceAccepts(final String name) {
    return SecretServices.REFERENCE_NAME_PATTERN.matcher(name).matches();
  }
}
