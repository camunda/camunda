/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.UpdateRetriesJobResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import java.time.Duration;
import org.junit.Test;
import org.mockito.Mockito;

public final class JobUpdateRetriesTest extends ClientTest {

  @Test
  public void shouldUpdateRetriesByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newUpdateRetriesCommand(jobKey).retries(newRetries).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getRetries()).isEqualTo(newRetries);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdateRetries() {
    // given
    final int newRetries = 23;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateRetriesCommand(job).retries(newRetries).send().join();

    // then
    final UpdateJobRetriesRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getRetries()).isEqualTo(newRetries);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newUpdateRetriesCommand(123).retries(3).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final UpdateRetriesJobCommandStep1 command = client.newUpdateRetriesCommand(12);

    // when
    final UpdateRetriesJobResponse response = command.retries(0).send().join();

    // then
    assertThat(response).isNotNull();
  }
}
