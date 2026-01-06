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
package io.camunda.client.impl.worker;

import static io.camunda.client.impl.worker.JobRunnableFactoryImpl.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.api.worker.JobHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

public class JobRunnableFactoryImplTest {
  @Test
  void shouldDecorateMDCContext() {
    // given
    final JobClient jobClient = mock(JobClient.class);
    final JobHandler jobHandler =
        (client, job) -> {
          // practically also a "then" as it executed by running the runnable
          assertThat(MDC.get(PROCESS_DEFINITION_KEY)).isEqualTo("123");
          assertThat(MDC.get(PROCESS_INSTANCE_KEY)).isEqualTo("234");
          assertThat(MDC.get(ELEMENT_INSTANCE_KEY)).isEqualTo("345");
          assertThat(MDC.get(JOB_KEY)).isEqualTo("456");
        };
    final JobExceptionHandler jobExceptionHandler = mock(JobExceptionHandler.class);
    final JobRunnableFactoryImpl jobRunnableFactory =
        new JobRunnableFactoryImpl(jobClient, jobHandler, jobExceptionHandler);
    final ActivatedJob activatedJob = mock(ActivatedJob.class);
    when(activatedJob.getProcessDefinitionKey()).thenReturn(123L);
    when(activatedJob.getProcessInstanceKey()).thenReturn(234L);
    when(activatedJob.getElementInstanceKey()).thenReturn(345L);
    when(activatedJob.getKey()).thenReturn(456L);
    final Runnable runnable = jobRunnableFactory.create(activatedJob, () -> {});
    MDC.put("OuterContext", "something");
    // when
    runnable.run();
    // then
    assertThat(MDC.get("OuterContext")).isEqualTo("something");
    assertThat(MDC.getCopyOfContextMap())
        .doesNotContainKeys(
            PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, ELEMENT_INSTANCE_KEY, JOB_KEY);
    MDC.remove("OuterContext");
  }
}
