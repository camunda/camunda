/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord.UserAttribute;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class UserRecordTest {
  @RegressionTest("https://github.com/camunda/camunda/issues/35177")
  void shouldSanitizePasswordOnToString() {
    // given
    final UserRecord userRecord = new UserRecord();
    userRecord.setUsername("myUser");
    userRecord.setPassword("mySecretPassword");

    // when
    final String userRecordString = userRecord.toString();

    // then
    assertThat(userRecordString)
        .isEqualTo(
            """
      {"userKey":-1,"username":"myUser","name":"","email":"","password":"***","externalId":"","active":true,"changedAttributes":[],"attributes":"gA=="}""");
  }

  @Test
  void shouldKeepChangedAttributesWhenCopied() {
    // given
    final UserRecord userRecord =
        new UserRecord()
            .setUsername("myUser")
            .setActive(false)
            .setChangedAttributes(Set.of(UserAttribute.ACTIVE, UserAttribute.EXTERNAL_ID));

    // when
    final UserRecord copy = userRecord.copy();

    // then
    assertThat(copy.getChangedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.ACTIVE, UserAttribute.EXTERNAL_ID);
    assertThat(copy.isActive()).isFalse();
    assertThat(copy.clearChangedAttributes().getChangedAttributes()).isEmpty();
  }

  @Test
  void shouldDefaultToActiveWithoutExternalIdAndMask() {
    // when
    final UserRecord userRecord = new UserRecord().setUsername("myUser");

    // then
    assertThat(userRecord.isActive()).isTrue();
    assertThat(userRecord.getExternalId()).isEmpty();
    assertThat(userRecord.getChangedAttributes()).isEmpty();
  }

  @Test
  void shouldRoundTripAttributesThroughCopy() {
    // given
    final var attributes =
        Map.of("department", "sales", "addresses", List.of(Map.of("city", "Berlin")));
    final UserRecord userRecord = new UserRecord().setUsername("myUser").setAttributes(attributes);

    // when
    final UserRecord copy = userRecord.copy();

    // then
    assertThat(copy.getAttributes()).isEqualTo(attributes);
  }

  @Test
  void shouldDefaultToEmptyAttributes() {
    // when
    final UserRecord userRecord = new UserRecord().setUsername("myUser");

    // then
    assertThat(userRecord.getAttributes()).isEmpty();
  }
}
