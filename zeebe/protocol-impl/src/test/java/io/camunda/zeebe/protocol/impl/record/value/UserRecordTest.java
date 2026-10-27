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
import io.camunda.zeebe.test.util.junit.RegressionTest;

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
      {"userKey":-1,"username":"myUser","name":"","email":"","password":"***"}""");
  }
}
