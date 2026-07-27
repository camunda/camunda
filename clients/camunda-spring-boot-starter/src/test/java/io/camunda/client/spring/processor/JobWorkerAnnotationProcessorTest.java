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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.ManagedJobWorker;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.annotation.processor.JobWorkerAnnotationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
public class JobWorkerAnnotationProcessorTest {

  @Mock private CamundaClient client;

  @Mock private JobWorkerManager jobWorkerManager;

  @Mock private MetricsRecorder metricsRecorder;

  @Mock private ParameterResolverStrategy parameterResolverStrategy;

  @Mock private ResultProcessorStrategy resultProcessorStrategy;

  @InjectMocks private JobWorkerAnnotationProcessor jobWorkerAnnotationProcessor;

  @Test
  public void shouldClearManagedJobWorkersOnStop() {
    // given - simulate two lifecycle rounds (as with @RepeatedTest)
    jobWorkerAnnotationProcessor.configureFor(beanInfo(new WithJobWorker()));
    jobWorkerAnnotationProcessor.start(client, null);

    // verify first round created exactly one job worker
    verify(jobWorkerManager, times(1))
        .createJobWorker(
            eq(client), any(ManagedJobWorker.class), eq(jobWorkerAnnotationProcessor), isNull());

    // when - stop should clear the managed job workers
    jobWorkerAnnotationProcessor.stop(client, null);

    // configure and start again (second test run)
    jobWorkerAnnotationProcessor.configureFor(beanInfo(new WithJobWorker()));
    jobWorkerAnnotationProcessor.start(client, null);

    // then - createJobWorker should have been called exactly once per lifecycle round (2 total),
    // not accumulating (which would be 3 if the list wasn't cleared)
    verify(jobWorkerManager, times(2))
        .createJobWorker(
            eq(client), any(ManagedJobWorker.class), eq(jobWorkerAnnotationProcessor), isNull());
  }

  @Test
  public void shouldRegisterWorkersOncePerClientOnPerClientStart() {
    // given a context with a single @JobWorker bean, scanned by onStart
    final ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"withJobWorker"});
    doReturn(WithJobWorker.class).when(applicationContext).getType("withJobWorker", false);
    jobWorkerAnnotationProcessor.setApplicationContext(applicationContext);

    final CamundaClient clientA = mock(CamundaClient.class);
    final CamundaClient clientB = mock(CamundaClient.class);

    // when - onStart runs once per client, as MultiCamundaLifecycleEventProducer will drive it
    jobWorkerAnnotationProcessor.onStart(clientA, "a");
    jobWorkerAnnotationProcessor.onStart(clientB, "b");

    // then - each client gets the worker registered exactly once; the re-scan on the second client
    // does not accumulate and re-register (which would be times(2) for client B)
    verify(jobWorkerManager, times(1))
        .createJobWorker(
            eq(clientA), any(ManagedJobWorker.class), eq(jobWorkerAnnotationProcessor), eq("a"));
    verify(jobWorkerManager, times(1))
        .createJobWorker(
            eq(clientB), any(ManagedJobWorker.class), eq(jobWorkerAnnotationProcessor), eq("b"));
  }

  private static final class WithJobWorker {
    @JobWorker
    public void myWorker() {}
  }
}
