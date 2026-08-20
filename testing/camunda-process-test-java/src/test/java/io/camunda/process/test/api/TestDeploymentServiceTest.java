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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.process.test.impl.deployment.TestDeploymentService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDeploymentServiceTest {

  @Mock private CamundaClient client;
  @Mock private DeployResourceCommandStep1 step1;
  @Mock private DeployResourceCommandStep1.DeployResourceCommandStep2 step2;
  @Mock private CamundaFuture<DeploymentEvent> future;
  @Mock private DeploymentEvent deploymentEvent;

  private final TestDeploymentService service = new TestDeploymentService();

  private void stubSuccessfulChain() {
    when(client.newDeployResourceCommand()).thenReturn(step1);
    when(step1.addResourceFromClasspath(anyString())).thenReturn(step2);
    when(step2.send()).thenReturn(future);
    when(future.join()).thenReturn(deploymentEvent);
  }

  @Test
  void shouldDeploysResourcesFromMethodAnnotation() throws Exception {
    // given
    stubSuccessfulChain();

    final Method method =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");

    // when
    service.deployTestResources(method, TestClassWithMethodAnnotation.class, client);

    // then
    verify(client).newDeployResourceCommand();
    verify(step1).addResourceFromClasspath("method-process.bpmn");
    verify(step2).send();
    verify(future).join();
    verifyNoMoreInteractions(step1, step2);
  }

  @Test
  void shouldDeploysResourcesFromClassAnnotation() throws Exception {
    // given
    stubSuccessfulChain();

    final Method method =
        TestClassWithClassAnnotation.class.getDeclaredMethod("testMethodWithoutAnnotation");

    // when
    service.deployTestResources(method, TestClassWithClassAnnotation.class, client);

    // then
    verify(client).newDeployResourceCommand();
    verify(step1).addResourceFromClasspath("class-process.bpmn");
  }

  @Test
  void shouldPreferMethodAnnotationOverClassAnnotation() throws Exception {
    // given
    stubSuccessfulChain();

    final Method method =
        TestClassWithBothAnnotations.class.getDeclaredMethod("testMethodWithDeployment");

    // when
    service.deployTestResources(method, TestClassWithBothAnnotations.class, client);

    // then
    verify(step1).addResourceFromClasspath("method-process.bpmn");
    verify(step1, never()).addResourceFromClasspath("class-process.bpmn");
  }

  @Test
  void shouldIgnoreDeploymentWithoutAnnotation() throws Exception {
    // given
    final Method method =
        TestClassWithoutAnnotation.class.getDeclaredMethod("testMethodWithoutDeployment");

    // when
    service.deployTestResources(method, TestClassWithoutAnnotation.class, client);

    // then
    verifyNoInteractions(client);
  }

  @Test
  void shouldIgnoreDeploymentWithoutResources() throws Exception {
    // given
    final Method method =
        TestClassWithEmptyResources.class.getDeclaredMethod("testMethodWithEmptyResources");

    // when
    service.deployTestResources(method, TestClassWithEmptyResources.class, client);

    // then
    verifyNoInteractions(client);
  }

  @Test
  void shouldFailIfCommandIsRejected() throws Exception {
    // given
    stubSuccessfulChain();
    final ClientException clientException = new ClientException("<expected: command rejected>");
    when(future.join()).thenThrow(clientException);

    final Method method =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");

    // when - then
    assertThatThrownBy(
            () -> service.deployTestResources(method, TestClassWithEmptyResources.class, client))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to deploy resources from @TestDeployment")
        .hasCause(clientException);
  }

  @Test
  void shouldFailIfResourceNotFound() throws Exception {
    // given
    when(client.newDeployResourceCommand()).thenReturn(step1);
    final ClientException clientException = new ClientException("<expected: resource not found>");
    when(step1.addResourceFromClasspath(anyString())).thenThrow(clientException);

    final Method method =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");

    // when - then
    assertThatThrownBy(
            () -> service.deployTestResources(method, TestClassWithEmptyResources.class, client))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to deploy resources from @TestDeployment")
        .hasCause(clientException);
  }

  @Test
  void shouldIgnoreDeploymentIfMethodIsNull() {
    // when
    service.deployTestResources(null, TestClassWithMethodAnnotation.class, client);

    // then
    verifyNoInteractions(client);
  }

  @Test
  void shouldDeployResourcesWithPaths() throws Exception {
    // given
    stubSuccessfulChain();
    when(step2.addResourceFromClasspath(anyString())).thenReturn(step2);

    final Method method =
        TestClassWithResourcePaths.class.getDeclaredMethod("testMethodWithResourcePaths");

    // when
    service.deployTestResources(method, TestClassWithResourcePaths.class, client);

    // then
    final ArgumentCaptor<String> resources = ArgumentCaptor.forClass(String.class);
    verify(step1, times(1)).addResourceFromClasspath(resources.capture());
    verify(step2, times(2)).addResourceFromClasspath(resources.capture());
    assertThat(resources.getAllValues())
        .containsExactly("coverage/process.bpmn", "decisions/decision.dmn", "forms/form.form");
  }

  // Helper test classes
  static class TestClassWithMethodAnnotation {
    @TestDeployment(resources = "method-process.bpmn")
    void testMethodWithDeployment() {}
  }

  @TestDeployment(resources = "class-process.bpmn")
  static class TestClassWithClassAnnotation {
    void testMethodWithoutAnnotation() {}
  }

  @TestDeployment(resources = "class-process.bpmn")
  static class TestClassWithBothAnnotations {
    @TestDeployment(resources = "method-process.bpmn")
    void testMethodWithDeployment() {}
  }

  static class TestClassWithoutAnnotation {
    void testMethodWithoutDeployment() {}
  }

  static class TestClassWithEmptyResources {
    @TestDeployment(resources = {})
    void testMethodWithEmptyResources() {}
  }

  static class TestClassWithMultipleResources {
    @TestDeployment(resources = {"process1.bpmn", "process2.bpmn", "decision.dmn"})
    void testMethodWithMultipleResources() {}
  }

  static class TestClassWithResourcePaths {
    @TestDeployment(
        resources = {"coverage/process.bpmn", "decisions/decision.dmn", "forms/form.form"})
    void testMethodWithResourcePaths() {}
  }
}
