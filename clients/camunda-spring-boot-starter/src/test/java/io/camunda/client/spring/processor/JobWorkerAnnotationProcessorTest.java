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
package io.camunda.client.spring.processor;

import static io.camunda.client.spring.testsupport.BeanInfoUtil.beanInfo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.spring.annotation.processor.JobWorkerAnnotationProcessor;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JobWorkerAnnotationProcessorTest {

  @Mock private CamundaClient client;

  @Mock private JobWorkerManager jobWorkerManager;

  @Test
  public void shouldClearJobWorkerValuesOnStop() {
    // given
    final JobWorkerAnnotationProcessor processor =
        new JobWorkerAnnotationProcessor(
            jobWorkerManager, Collections.<JobWorkerValueCustomizer>emptyList());

    // simulate two lifecycle rounds (as with @RepeatedTest)
    processor.configureFor(beanInfo(new WithJobWorker()));
    processor.start(client);

    // verify first round opened exactly one job worker
    verify(jobWorkerManager, times(1)).openWorker(eq(client), any(JobWorkerValue.class));

    // when - stop should clear the job worker values
    processor.stop(client);

    // configure and start again (second test run)
    processor.configureFor(beanInfo(new WithJobWorker()));
    processor.start(client);

    // then - openWorker should have been called exactly once per lifecycle round (2 total),
    // not accumulating (which would be 3 if the list wasn't cleared)
    verify(jobWorkerManager, times(2)).openWorker(eq(client), any(JobWorkerValue.class));
  }

  private static final class WithJobWorker {
    @JobWorker
    public void myWorker() {}
  }
}
