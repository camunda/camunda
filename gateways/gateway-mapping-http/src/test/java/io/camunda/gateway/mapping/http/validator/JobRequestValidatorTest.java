/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.JobChangeset;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import org.junit.jupiter.api.Test;

class JobRequestValidatorTest {

  @Test
  void shouldAcceptChangesetWithOnlyPriority() {
    // given
    final var changeset = JobChangeset.Builder.create().priority(50).build();
    final var request = JobUpdateRequest.Builder.create().changeset(changeset).build();

    // when
    final var result = JobRequestValidator.validateJobUpdateRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectNullChangeset() {
    // given
    final var request = JobUpdateRequest.Builder.create().changeset(null).build();

    // when
    final var result = JobRequestValidator.validateJobUpdateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("retries", "timeout", "priority");
  }

  @Test
  void shouldRejectChangesetWithAllNullFields() {
    // given
    final var changeset = JobChangeset.Builder.create().build();
    final var request = JobUpdateRequest.Builder.create().changeset(changeset).build();

    // when
    final var result = JobRequestValidator.validateJobUpdateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("retries", "timeout", "priority");
  }
}
