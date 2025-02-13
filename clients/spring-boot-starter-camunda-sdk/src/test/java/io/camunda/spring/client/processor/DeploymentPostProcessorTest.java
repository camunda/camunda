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
package io.camunda.spring.client.processor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.spring.client.annotation.Deployment;
import io.camunda.spring.client.annotation.processor.DeploymentAnnotationProcessor;
import io.camunda.spring.client.bean.ClassInfo;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
public class DeploymentPostProcessorTest {

  @Mock private CamundaClient client;

  @Mock private DeployResourceCommandStep1 deployStep1;

  @Mock private DeployResourceCommandStep1.DeployResourceCommandStep2 deployStep2;

  @Mock private CamundaFuture<DeploymentEvent> camundaFuture;

  @Mock private DeploymentEvent deploymentEvent;

  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks private DeploymentAnnotationProcessor deploymentPostProcessor;

  @BeforeEach
  public void init() {
    deploymentPostProcessor = spy(new DeploymentAnnotationProcessor(applicationEventPublisher));
  }

  @Test
  public void shouldDeploySingleResourceTest() {
    // given
    final ClassInfo classInfo = ClassInfo.builder().bean(new WithSingleClassPathResource()).build();

    final Resource resource = mock(FileSystemResource.class);

    when(resource.getFilename()).thenReturn("1.bpmn");

    when(client.newDeployResourceCommand()).thenReturn(deployStep1);

    when(deploymentPostProcessor.getResources(anyString())).thenReturn(new Resource[] {resource});

    when(deployStep1.addResourceStream(any(), anyString())).thenReturn(deployStep2);

    when(deployStep2.send()).thenReturn(camundaFuture);

    when(camundaFuture.join()).thenReturn(deploymentEvent);

    when(deploymentEvent.getProcesses()).thenReturn(Collections.singletonList(getProcess()));

    // when
    deploymentPostProcessor.configureFor(classInfo);
    deploymentPostProcessor.start(client);

    // then
    verify(deployStep1).addResourceStream(any(), eq("1.bpmn"));
    verify(deployStep2).send();
    verify(camundaFuture).join();
  }

  @Test
  public void shouldDeployMultipleResourcesTest() {
    // given
    final ClassInfo classInfo = ClassInfo.builder().bean(new WithDoubleClassPathResource()).build();

    final Resource[] resources = {mock(FileSystemResource.class), mock(FileSystemResource.class)};

    when(resources[0].getFilename()).thenReturn("1.bpmn");
    when(resources[1].getFilename()).thenReturn("2.bpmn");

    when(client.newDeployResourceCommand()).thenReturn(deployStep1);

    when(deploymentPostProcessor.getResources("classpath*:/1.bpmn"))
        .thenReturn(new Resource[] {resources[0]});

    when(deploymentPostProcessor.getResources("classpath*:/2.bpmn"))
        .thenReturn(new Resource[] {resources[1]});

    when(deployStep1.addResourceStream(any(), anyString())).thenReturn(deployStep2);

    when(deployStep2.send()).thenReturn(camundaFuture);

    when(camundaFuture.join()).thenReturn(deploymentEvent);

    when(deploymentEvent.getProcesses()).thenReturn(Collections.singletonList(getProcess()));

    // when
    deploymentPostProcessor.configureFor(classInfo);
    deploymentPostProcessor.start(client);

    // then
    verify(deployStep1).addResourceStream(any(), eq("1.bpmn"));
    verify(deployStep1).addResourceStream(any(), eq("1.bpmn"));

    verify(deployStep2).send();
    verify(camundaFuture).join();
  }

  @Test
  public void shouldThrowExceptionOnNoResourcesToDeploy() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          // given
          final ClassInfo classInfo =
              ClassInfo.builder().bean(new WithNoClassPathResource()).build();

          when(client.newDeployResourceCommand()).thenReturn(deployStep1);

          // when
          deploymentPostProcessor.configureFor(classInfo);
          deploymentPostProcessor.start(client);
        });
  }

  private Process getProcess() {
    return new Process() {
      @Override
      public String getBpmnProcessId() {
        return "12345-abcd";
      }

      @Override
      public int getVersion() {
        return 1;
      }

      @Override
      public long getProcessDefinitionKey() {
        return 101010;
      }

      @Override
      public String getResourceName() {
        return "TestProcess";
      }

      @Override
      public String getTenantId() {
        return "TestTenantId";
      }
    };
  }

  @Deployment(resources = "/1.bpmn")
  private static final class WithSingleClassPathResource {}

  @Deployment(resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static final class WithDoubleClassPathResource {}

  @Deployment(resources = {})
  private static final class WithNoClassPathResource {}
}
