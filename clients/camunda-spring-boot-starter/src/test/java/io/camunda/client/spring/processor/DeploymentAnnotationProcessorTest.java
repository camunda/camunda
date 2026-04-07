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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.AnnotationUtil;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.annotation.value.DeploymentValue;
import io.camunda.client.annotation.value.SourceAware.Empty;
import io.camunda.client.annotation.value.SourceAware.FromAnnotation;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.spring.annotation.processor.DeploymentAnnotationProcessor;
import io.camunda.client.spring.properties.CamundaClientDeploymentProperties;
import io.camunda.client.spring.properties.CamundaClientProperties;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
public class DeploymentAnnotationProcessorTest {

  @Mock private CamundaClient client;

  @Mock private DeployResourceCommandStep1 deployStep1;

  @Mock private DeployResourceCommandStep1.DeployResourceCommandStep2 deployStep2;

  @Mock private DeploymentEvent deploymentEvent;

  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @Mock private ResourcePatternResolver resourcePatternResolver;

  @Mock private Function<BeanInfo, List<DeploymentValue>> deploymentValueExtractor;

  @Mock private CamundaClientProperties camundaClientProperties;

  @InjectMocks private DeploymentAnnotationProcessor deploymentAnnotationProcessor;

  @BeforeEach
  public void init() {
    final CamundaClientDeploymentProperties deploymentProperties =
        new CamundaClientDeploymentProperties();
    when(camundaClientProperties.getDeployment()).thenReturn(deploymentProperties);
  }

  @Test
  public void shouldDeploySingleResourceTest() throws IOException {
    // given
    when(deploymentValueExtractor.apply(any()))
        .thenAnswer(r -> AnnotationUtil.getDeploymentValues(r.getArgument(0)));
    final BeanInfo classInfo = beanInfo(new WithSingleClassPathResource());

    final Resource resource = mock(FileSystemResource.class);

    when(resource.getFilename()).thenReturn("1.bpmn");

    when(client.newDeployResourceCommand()).thenReturn(deployStep1);

    when(resourcePatternResolver.getResources(anyString())).thenReturn(new Resource[] {resource});

    when(deployStep1.addResourceStream(any(), anyString())).thenReturn(deployStep2);

    when(deployStep2.execute()).thenReturn(deploymentEvent);

    when(deploymentEvent.getProcesses()).thenReturn(Collections.singletonList(getProcess()));

    // when
    deploymentAnnotationProcessor.configureFor(classInfo);
    deploymentAnnotationProcessor.start(client);

    // then
    verify(deployStep1).addResourceStream(any(), eq("1.bpmn"));
    verify(deployStep2).execute();
  }

  @Test
  public void shouldDeployMultipleResourcesTest() throws IOException {
    // given
    when(deploymentValueExtractor.apply(any()))
        .thenAnswer(r -> AnnotationUtil.getDeploymentValues(r.getArgument(0)));
    final BeanInfo classInfo = beanInfo(new WithDoubleClassPathResource());

    final Resource[] resources = {mock(FileSystemResource.class), mock(FileSystemResource.class)};

    when(resources[0].getFilename()).thenReturn("1.bpmn");
    when(resources[1].getFilename()).thenReturn("2.bpmn");

    when(client.newDeployResourceCommand()).thenReturn(deployStep1);

    when(resourcePatternResolver.getResources("classpath*:/1.bpmn"))
        .thenReturn(new Resource[] {resources[0]});

    when(resourcePatternResolver.getResources("classpath*:/2.bpmn"))
        .thenReturn(new Resource[] {resources[1]});

    when(deployStep1.addResourceStream(any(), anyString())).thenReturn(deployStep2);
    when(deployStep2.addResourceStream(any(), anyString())).thenReturn(deployStep2);

    when(deployStep2.execute()).thenReturn(deploymentEvent);

    when(deploymentEvent.getProcesses()).thenReturn(Collections.singletonList(getProcess()));

    // when
    deploymentAnnotationProcessor.configureFor(classInfo);
    deploymentAnnotationProcessor.start(client);

    // then
    verify(deployStep1).addResourceStream(any(), eq("1.bpmn"));
    verify(deployStep2).addResourceStream(any(), eq("2.bpmn"));

    verify(deployStep2).execute();
  }

  @Test
  public void shouldDeployDistinctResources() throws IOException {
    // given
    when(deploymentValueExtractor.apply(any()))
        .thenAnswer(r -> AnnotationUtil.getDeploymentValues(r.getArgument(0)));
    final BeanInfo classInfo = beanInfo(new WithDoubleClassPathResource());
    final Resource resource = mock(FileSystemResource.class);
    final Resource[] resources = {resource, resource};

    when(resources[0].getFilename()).thenReturn("1.bpmn");
    when(resources[1].getFilename()).thenReturn("1.bpmn");

    when(client.newDeployResourceCommand()).thenReturn(deployStep1);

    when(resourcePatternResolver.getResources("classpath*:/1.bpmn"))
        .thenReturn(new Resource[] {resources[0]});

    when(resourcePatternResolver.getResources("classpath*:/2.bpmn"))
        .thenReturn(new Resource[] {resources[1]});

    when(deployStep1.addResourceStream(any(), anyString())).thenReturn(deployStep2);

    when(deployStep2.execute()).thenReturn(deploymentEvent);

    when(deploymentEvent.getProcesses()).thenReturn(Collections.singletonList(getProcess()));

    // when
    deploymentAnnotationProcessor.configureFor(classInfo);
    deploymentAnnotationProcessor.start(client);

    // then
    verify(deployStep1, times(1)).addResourceStream(any(), eq("1.bpmn"));

    verify(deployStep2).execute();
  }

  @Test
  public void shouldThrowExceptionOnNoResourcesToDeploy() {
    when(deploymentValueExtractor.apply(any()))
        .thenAnswer(r -> AnnotationUtil.getDeploymentValues(r.getArgument(0)));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              // given
              final BeanInfo classInfo = beanInfo(new WithNoClassPathResource());

              // when
              deploymentAnnotationProcessor.configureFor(classInfo);
              deploymentAnnotationProcessor.start(client);
            });
  }

  @Test
  void shouldNotDeployResourceFromOtherJar() throws IOException {
    // given
    final FileSystemResource resource = mock(FileSystemResource.class);
    // the resource is from the spring boot starter while the bean from the java client
    when(resource.getURL())
        .thenReturn(
            DeploymentAnnotationProcessor.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation());
    when(deploymentValueExtractor.apply(any()))
        .thenReturn(
            List.of(
                new DeploymentValue(
                    List.of("classpath*:/1.bpmn"),
                    null,
                    new FromAnnotation<>(true),
                    CamundaClient.class)));
    when(resourcePatternResolver.getResources("classpath*:/1.bpmn"))
        .thenReturn(new Resource[] {resource});
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {

              // when
              deploymentAnnotationProcessor.configureFor(mock(BeanInfo.class));
              deploymentAnnotationProcessor.start(client);
            });
  }

  @Test
  void shouldOverrideDefaultBehavior() throws IOException {
    // given
    final FileSystemResource resource = mock(FileSystemResource.class);
    camundaClientProperties.getDeployment().setOwnJarOnly(true);
    // the resource is from the spring boot starter while the bean from the java client
    when(resource.getURL())
        .thenReturn(
            DeploymentAnnotationProcessor.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation());
    when(deploymentValueExtractor.apply(any()))
        .thenReturn(
            List.of(
                new DeploymentValue(
                    List.of("classpath*:/1.bpmn"), null, new Empty<>(), CamundaClient.class)));
    when(resourcePatternResolver.getResources("classpath*:/1.bpmn"))
        .thenReturn(new Resource[] {resource});
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {

              // when
              deploymentAnnotationProcessor.configureFor(mock(BeanInfo.class));
              deploymentAnnotationProcessor.start(client);
            });
  }

  @Test
  public void shouldClearDeploymentValuesOnStop() throws IOException {
    // given
    when(deploymentValueExtractor.apply(any()))
        .thenAnswer(r -> AnnotationUtil.getDeploymentValues(r.getArgument(0)));
    final BeanInfo classInfo = beanInfo(new WithSingleClassPathResource());

    final Resource resource = mock(FileSystemResource.class);
    when(resource.getFilename()).thenReturn("1.bpmn");
    when(client.newDeployResourceCommand()).thenReturn(deployStep1);
    when(resourcePatternResolver.getResources(anyString())).thenReturn(new Resource[] {resource});
    when(deployStep1.addResourceStream(any(), anyString())).thenReturn(deployStep2);
    when(deployStep2.execute()).thenReturn(deploymentEvent);
    when(deploymentEvent.getProcesses()).thenReturn(Collections.singletonList(getProcess()));

    // when - simulate two lifecycle rounds (as with @RepeatedTest)
    deploymentAnnotationProcessor.configureFor(classInfo);
    deploymentAnnotationProcessor.start(client);

    // stop should clear the deployment values
    deploymentAnnotationProcessor.stop(client);

    // configure and start again (second test run)
    deploymentAnnotationProcessor.configureFor(classInfo);
    deploymentAnnotationProcessor.start(client);

    // then - deploy should have been called exactly once per lifecycle round, not accumulating
    verify(deployStep2, times(2)).execute();
    verify(deployStep1, times(2)).addResourceStream(any(), eq("1.bpmn"));
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

      @Override
      public String getName() {
        return "TestProcessName";
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
