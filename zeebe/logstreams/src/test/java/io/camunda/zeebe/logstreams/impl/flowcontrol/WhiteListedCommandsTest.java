/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class WhiteListedCommandsTest {

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"COMPLETE", "FAIL", "YIELD", "TIME_OUT"})
  void shouldWhitelistJobCommandsThatFinishWork(final JobIntent intent) {
    // when - then
    assertThat(WhiteListedCommands.isWhitelisted(intent)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"COMPLETE", "ASSIGN", "CLAIM"})
  void shouldWhitelistUserTaskCommandsEquivalentToJobCommands(final UserTaskIntent intent) {
    // when - then
    assertThat(WhiteListedCommands.isWhitelisted(intent)).isTrue();
  }

  @Test
  void shouldNotWhitelistOtherUserTaskCommands() {
    // given
    final Intent update = UserTaskIntent.UPDATE;

    // when - then
    assertThat(WhiteListedCommands.isWhitelisted(update)).isFalse();
  }

  @Test
  void shouldNotWhitelistCommandsThatAddLoad() {
    // when - then
    assertThat(WhiteListedCommands.isWhitelisted(ProcessInstanceCreationIntent.CREATE)).isFalse();
  }

  @Test
  void shouldNotWhitelistNullIntent() {
    // when - then
    assertThat(WhiteListedCommands.isWhitelisted(null)).isFalse();
  }
}
