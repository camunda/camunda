/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MappingTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateMapping() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRecord =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();

    final var createMapping = mappingRecord.getValue();
    Assertions.assertThat(createMapping)
        .isNotNull()
        .hasFieldOrProperty("mappingKey")
        .hasFieldOrPropertyWithValue("claimName", claimName)
        .hasFieldOrPropertyWithValue("claimValue", claimValue);
  }

  @Test
  public void shouldNotDuplicate() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mapping()
            .newMapping(claimName)
            .withClaimValue(claimValue)
            .expectRejection()
            .create();

    assertThat(duplicatedMappingRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping with claimName '%s' and claimValue '%s', but a mapping with this claim already exists.",
                claimName, claimValue));
  }
}
