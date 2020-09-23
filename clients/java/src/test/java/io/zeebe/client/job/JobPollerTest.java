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
package io.zeebe.client.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.worker.JobPoller;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class JobPollerTest extends ClientTest {

  private Consumer<ActivatedJob> jobConsumer;
  private IntConsumer doneCallback;
  private Consumer<Throwable> errorCallback;

  @Before
  public void setup() {
    jobConsumer = Mockito.spy(Consumer.class);
    doneCallback = Mockito.spy(IntConsumer.class);
    errorCallback = Mockito.spy(Consumer.class);
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(123);
    final JobPoller jobPoller = getJobPoller(requestTimeout);

    // when
    jobPoller.poll(123, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldCallbackWhenPollComplete() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job(), TestData.job());

    // when
    getJobPoller().poll(123, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(jobConsumer, times(2)).accept(any(ActivatedJob.class));
              verify(doneCallback).accept(eq(2));
              verify(errorCallback, never()).accept(any(Throwable.class));
            });
  }

  @Test
  public void shouldCallbackWhenPollFailed() {
    // given
    gatewayService.onActivateJobsRequest(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));

    // when
    getJobPoller().poll(123, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(jobConsumer, never()).accept(any(ActivatedJob.class));
              verify(doneCallback, never()).accept(any(Integer.class));
              verify(errorCallback).accept(any(StatusRuntimeException.class));
            });
  }

  private JobPoller getJobPoller() {
    return getJobPoller(Duration.ofSeconds(10));
  }

  private JobPoller getJobPoller(final Duration requestTimeout) {
    return new JobPoller(
        rule.getGatewayStub(),
        ActivateJobsRequest.newBuilder(),
        new ZeebeObjectMapper(),
        requestTimeout,
        (t) -> false);
  }

  private static final class TestData {
    private static GatewayOuterClass.ActivatedJob job() {
      return GatewayOuterClass.ActivatedJob.newBuilder()
          .setKey(12)
          .setType("foo")
          .setWorkflowInstanceKey(123)
          .setBpmnProcessId("test1")
          .setWorkflowDefinitionVersion(2)
          .setWorkflowKey(23)
          .setElementId("foo")
          .setElementInstanceKey(23213)
          .setCustomHeaders("{\"version\": \"1\"}")
          .setWorker("worker1")
          .setRetries(34)
          .setDeadline(1231)
          .setVariables("{\"key\": \"val\"}")
          .build();
    }
  }
}
