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
package io.camunda.client.jobhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware.FromAnnotation;
import io.camunda.client.api.worker.JobWorker;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the multi-client fan-out mechanism of {@link JobWorkerManager}: the same job type
 * can be registered independently on multiple clients, and closing is scoped to a single client.
 */
public class JobWorkerManagerTest {

  private static final String TYPE = "my-type";
  private static final Object SOURCE = new Object();

  private JobWorkerFactory jobWorkerFactory;
  private JobWorkerManager jobWorkerManager;

  @BeforeEach
  void setUp() {
    jobWorkerFactory = mock(JobWorkerFactory.class);
    jobWorkerManager = new JobWorkerManager(List.of(), jobWorkerFactory);
  }

  @Test
  void shouldRegisterSameTypeIndependentlyOnMultipleClients() {
    // given
    final CamundaClient clientA = mock(CamundaClient.class);
    final CamundaClient clientB = mock(CamundaClient.class);
    when(jobWorkerFactory.createJobWorker(eq(clientA), any(), any()))
        .thenReturn(mock(JobWorker.class));
    when(jobWorkerFactory.createJobWorker(eq(clientB), any(), any()))
        .thenReturn(mock(JobWorker.class));

    // when - the same type is registered on two different clients
    jobWorkerManager.createJobWorker(clientA, managedWorker(TYPE), SOURCE, "a");
    jobWorkerManager.createJobWorker(clientB, managedWorker(TYPE), SOURCE, "b");

    // then - a worker is opened on each client, not just once
    verify(jobWorkerFactory).createJobWorker(eq(clientA), any(), any());
    verify(jobWorkerFactory).createJobWorker(eq(clientB), any(), any());
  }

  @Test
  void shouldCloseOnlyTheGivenClientsWorkers() {
    // given - the same type registered on two clients
    final CamundaClient clientA = mock(CamundaClient.class);
    final CamundaClient clientB = mock(CamundaClient.class);
    final JobWorker workerA = mock(JobWorker.class);
    final JobWorker workerB = mock(JobWorker.class);
    when(jobWorkerFactory.createJobWorker(eq(clientA), any(), any())).thenReturn(workerA);
    when(jobWorkerFactory.createJobWorker(eq(clientB), any(), any())).thenReturn(workerB);
    jobWorkerManager.createJobWorker(clientA, managedWorker(TYPE), SOURCE, "a");
    jobWorkerManager.createJobWorker(clientB, managedWorker(TYPE), SOURCE, "b");

    // when - only client A's workers are closed
    jobWorkerManager.closeJobWorkers(SOURCE, clientA);

    // then - client A's worker is closed, client B's is left running
    verify(workerA).close();
    verify(workerB, never()).close();
  }

  @Test
  void shouldCloseWorkersPerClientSequentially() {
    // given
    final CamundaClient clientA = mock(CamundaClient.class);
    final CamundaClient clientB = mock(CamundaClient.class);
    final JobWorker workerA = mock(JobWorker.class);
    final JobWorker workerB = mock(JobWorker.class);
    when(jobWorkerFactory.createJobWorker(eq(clientA), any(), any())).thenReturn(workerA);
    when(jobWorkerFactory.createJobWorker(eq(clientB), any(), any())).thenReturn(workerB);
    jobWorkerManager.createJobWorker(clientA, managedWorker(TYPE), SOURCE, "a");
    jobWorkerManager.createJobWorker(clientB, managedWorker(TYPE), SOURCE, "b");

    // when
    jobWorkerManager.closeJobWorkers(SOURCE, clientA);
    jobWorkerManager.closeJobWorkers(SOURCE, clientB);

    // then - each client's worker is eventually closed exactly once
    verify(workerA).close();
    verify(workerB).close();
  }

  @Test
  void shouldExposeTypeAcrossClients() {
    // given
    final CamundaClient clientA = mock(CamundaClient.class);
    final CamundaClient clientB = mock(CamundaClient.class);
    when(jobWorkerFactory.createJobWorker(any(), any(), any())).thenReturn(mock(JobWorker.class));
    jobWorkerManager.createJobWorker(clientA, managedWorker(TYPE), SOURCE, "a");
    jobWorkerManager.createJobWorker(clientB, managedWorker(TYPE), SOURCE, "b");

    // when / then - the type is discoverable regardless of how many clients run it
    assertThat(jobWorkerManager.getJobWorkers()).containsKey(TYPE);
    assertThat(jobWorkerManager.getJobWorker(TYPE)).isNotNull();
  }

  private static ManagedJobWorker managedWorker(final String type) {
    final JobWorkerValue value = new JobWorkerValue();
    value.setType(new FromAnnotation<>(type));
    value.setName(new FromAnnotation<>(type + "-worker"));
    value.setEnabled(new FromAnnotation<>(true));
    return new ManagedJobWorker(value, mock(JobHandlerFactory.class));
  }
}
