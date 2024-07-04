/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.UpdateTimeoutJobResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import java.time.Duration;
import org.junit.Test;
import org.mockito.Mockito;

public class JobUpdateTimeoutTest extends ClientTest {

  @Test
  public void shouldUpdateTimeoutByKeyMillis() {
    // given
    final long jobKey = 12;
    final long timeout = 100;

    // when
    client.newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    final UpdateJobTimeoutRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getTimeout()).isEqualTo(timeout);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdateTimeoutByKeyDuration() {
    // given
    final long jobKey = 12;
    final Duration timeout = Duration.ofMinutes(15);

    // when
    client.newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    final UpdateJobTimeoutRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getTimeout()).isEqualTo(timeout.toMillis());

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdateTimeoutMillis() {
    // given
    final long timeout = 100;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateTimeoutCommand(job).timeout(timeout).send().join();

    // then
    final UpdateJobTimeoutRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getTimeout()).isEqualTo(timeout);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldUpdateTimeoutDuration() {
    // given
    final Duration timeout = Duration.ofMinutes(10);
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateTimeoutCommand(job).timeout(timeout).send().join();

    // then
    final UpdateJobTimeoutRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getTimeout()).isEqualTo(timeout.toMillis());

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newUpdateTimeoutCommand(123).timeout(100).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final UpdateTimeoutJobCommandStep1 command = client.newUpdateTimeoutCommand(12);

    // when
    final UpdateTimeoutJobResponse response = command.timeout(10).send().join();

    // then
    assertThat(response).isNotNull();
  }
}
