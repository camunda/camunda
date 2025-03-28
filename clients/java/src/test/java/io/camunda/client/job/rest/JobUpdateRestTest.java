/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.job.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.ResponseMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.protocol.rest.JobChangeset;
import io.camunda.client.protocol.rest.JobUpdateRequest;
import io.camunda.client.util.ClientRestTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class JobUpdateRestTest extends ClientRestTest {

  @Test
  public void shouldUpdateRetriesByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newUpdateRetriesCommand(jobKey).retries(newRetries).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
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
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
  }

  @Test
  public void shouldUpdateTimeoutByKeyMillis() {
    // given
    final long jobKey = 12;
    final Long timeout = 100L;

    // when
    client.newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(timeout);
  }

  @Test
  public void shouldUpdateTimeoutByKeyDuration() {
    // given
    final long jobKey = 12;
    final Duration timeout = Duration.ofMinutes(15);

    // when
    client.newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(timeout.toMillis());
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
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(timeout);
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
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(timeout.toMillis());
  }

  @Test
  public void shouldUpdateRetriesAndTimeoutByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;
    final long newTimeout = 100L;
    final JobChangeset changeset = new JobChangeset().retries(newRetries).timeout(newTimeout);

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout);
  }

  @Test
  public void shouldUpdateOnlyRetriesByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;
    final JobChangeset changeset = new JobChangeset().retries(newRetries);

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
    assertThat(request.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldUpdateOnlyTimeoutByKey() {
    // given
    final long jobKey = 12;
    final long newTimeout = 100L;
    final JobChangeset changeset = new JobChangeset().timeout(newTimeout);

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout);
  }

  @Test
  public void shouldUpdateRetriesAndTimeoutByKeyMultiparam() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;
    final long newTimeout = 100L;

    // when
    client.newUpdateJobCommand(jobKey).update(newRetries, newTimeout).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout);
  }

  @Test
  public void shouldUpdateOnlyRetriesByKeyMultiParam() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newUpdateJobCommand(jobKey).update(newRetries, null).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
    assertThat(request.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldUpdateOnlyRetries() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;
    final JobChangeset changeset = new JobChangeset().retries(newRetries);

    // when
    client.newUpdateJobCommand(jobKey).updateRetries(newRetries).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
    assertThat(request.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldUpdateOnlyTimeoutByKeyMultiParam() {
    // given
    final long jobKey = 12;
    final long newTimeout = 100L;

    // when
    client.newUpdateJobCommand(jobKey).update(null, newTimeout).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout);
  }

  @Test
  public void shouldUpdateOnlyTimeout() {
    // given
    final long jobKey = 12;
    final long newTimeout = 100L;

    // when
    client.newUpdateJobCommand(jobKey).updateTimeout(newTimeout).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout);
  }

  @Test
  public void shouldUpdateOnlyTimeoutDuration() {
    // given
    final long jobKey = 12;
    final Duration newTimeout = Duration.ofMinutes(15);

    // when
    client.newUpdateJobCommand(jobKey).updateTimeout(newTimeout).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout.toMillis());
  }

  @Test
  public void shouldUpdateRetriesAndTimeout() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    final int newRetries = 23;
    final long newTimeout = 100L;
    final JobChangeset changeset = new JobChangeset().retries(newRetries).timeout(newTimeout);

    // when
    client
        .newUpdateJobCommand(job)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isEqualTo(newRetries);
    assertThat(request.getChangeset().getTimeout()).isEqualTo(newTimeout);
  }

  @Test
  public void shouldSendNullValuesByKey() {
    // given
    final long jobKey = 12;
    final JobChangeset changeset = new JobChangeset();

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldSendNullValues() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);
    final JobChangeset changeset = new JobChangeset();

    // when
    client
        .newUpdateJobCommand(job)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isNull();
  }

  @Test
  public void shouldAcceptNullChangeset() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateJobCommand(job).update(null).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset()).isNull();
  }

  @Test
  public void shouldSendNullValuesMultiParam() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newUpdateJobCommand(job).update(null, null).send().join();

    // then
    final JobUpdateRequest request = gatewayService.getLastRequest(JobUpdateRequest.class);
    assertThat(request.getChangeset().getRetries()).isNull();
    assertThat(request.getChangeset().getTimeout()).isNull();
  }
}
