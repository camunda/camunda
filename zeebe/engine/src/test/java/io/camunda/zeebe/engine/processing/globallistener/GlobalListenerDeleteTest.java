/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GlobalListenerDeleteTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteExistingListener() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventType("all").create();

    // when
    final var result = engine.globalListener().withId("my-id").delete();

    // then
    assertThat(result.getValue()).hasId("my-id");
  }

  @Test
  public void shouldNotDeleteListenerWithMissingId() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventType("all").create();

    // when
    final var rejection = engine.globalListener().expectRejection().delete();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotDeleteListenerIfItIsNotFound() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventType("creating").create();

    // when
    final var rejection = engine.globalListener().withId("my-other-id").expectRejection().delete();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
