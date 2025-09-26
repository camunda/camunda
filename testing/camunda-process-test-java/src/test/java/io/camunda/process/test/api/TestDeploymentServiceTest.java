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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.process.test.api.deployment.TestDeployment;
import io.camunda.process.test.api.deployment.TestDeploymentService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDeploymentServiceTest {

  @Mock private CamundaClient mockClient;
  @Mock private DeployResourceCommandStep1 mockDeployCommandStep1;
  @Mock private DeployResourceCommandStep1.DeployResourceCommandStep2 mockDeployCommandStep2;
  @Mock private CamundaFuture<DeploymentEvent> mockFuture;
  @Mock private DeploymentEvent mockDeploymentEvent;

  private TestDeploymentService testDeploymentService;

  @BeforeEach
  void setUp() {
    testDeploymentService = new TestDeploymentService();
  }

  private void setupDeploymentMocks() {
    lenient().when(mockClient.newDeployResourceCommand()).thenReturn(mockDeployCommandStep1);
    lenient()
        .when(mockDeployCommandStep1.addResourceFromClasspath(anyString()))
        .thenReturn(mockDeployCommandStep2);
    lenient().when(mockDeployCommandStep2.send()).thenReturn(mockFuture);
    lenient().when(mockFuture.join()).thenReturn(mockDeploymentEvent);
    lenient().when(mockDeploymentEvent.getKey()).thenReturn(12345L);
  }

  @Test
  void shouldDeployResourcesWhenMethodAnnotationIsPresent() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");
    final Class<?> testClass = TestClassWithMethodAnnotation.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    verify(mockClient).newDeployResourceCommand();
    verify(mockDeployCommandStep1).addResourceFromClasspath("method-process.bpmn");
    verify(mockDeployCommandStep2).send();
    verify(mockFuture).join();
  }

  @Test
  void shouldDeployResourcesWhenClassAnnotationIsPresent() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithClassAnnotation.class.getDeclaredMethod("testMethodWithoutAnnotation");
    final Class<?> testClass = TestClassWithClassAnnotation.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    verify(mockClient).newDeployResourceCommand();
    verify(mockDeployCommandStep1).addResourceFromClasspath("class-process.bpmn");
    verify(mockDeployCommandStep2).send();
    verify(mockFuture).join();
  }

  @Test
  void shouldDeployMultipleResources() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithMultipleResources.class.getDeclaredMethod("testMethodWithMultipleResources");
    final Class<?> testClass = TestClassWithMultipleResources.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    // Each resource gets its own deployment
    verify(mockClient, times(3)).newDeployResourceCommand();
    verify(mockDeployCommandStep1).addResourceFromClasspath("process1.bpmn");
    verify(mockDeployCommandStep1).addResourceFromClasspath("process2.bpmn");
    verify(mockDeployCommandStep1).addResourceFromClasspath("decision.dmn");
    verify(mockDeployCommandStep2, times(3)).send();
    verify(mockFuture, times(3)).join();
  }

  @Test
  void shouldPrioritizeMethodAnnotationOverClassAnnotation() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithBothAnnotations.class.getDeclaredMethod("testMethodWithDeployment");
    final Class<?> testClass = TestClassWithBothAnnotations.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    verify(mockDeployCommandStep1).addResourceFromClasspath("method-process.bpmn");
    verify(mockDeployCommandStep1, never()).addResourceFromClasspath("class-process.bpmn");
    verify(mockDeployCommandStep2).send();
  }

  @Test
  void shouldNotDeployWhenNoAnnotationIsPresent() throws Exception {
    // given
    final Method testMethod =
        TestClassWithoutAnnotation.class.getDeclaredMethod("testMethodWithoutDeployment");
    final Class<?> testClass = TestClassWithoutAnnotation.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    verify(mockClient, never()).newDeployResourceCommand();
    verify(mockDeployCommandStep1, never()).addResourceFromClasspath(anyString());
  }

  @Test
  void shouldNotDeployWhenResourcesArrayIsEmpty() throws Exception {
    // given
    final Method testMethod =
        TestClassWithEmptyResources.class.getDeclaredMethod("testMethodWithEmptyResources");
    final Class<?> testClass = TestClassWithEmptyResources.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    verify(mockClient, never()).newDeployResourceCommand();
    verify(mockDeployCommandStep1, never()).addResourceFromClasspath(anyString());
  }

  @Test
  void shouldHandleDeploymentFailure() throws Exception {
    // given
    setupDeploymentMocks();
    when(mockFuture.join()).thenThrow(new RuntimeException("Deployment failed"));
    final Method testMethod =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");
    final Class<?> testClass = TestClassWithMethodAnnotation.class;

    assertThatThrownBy(
            () -> testDeploymentService.deployTestResources(testMethod, testClass, mockClient))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to deploy test resources")
        .hasMessageContaining("testMethodWithDeployment");
  }

  @Test
  void shouldHandleResourceNotFound() throws Exception {
    // given
    lenient().when(mockClient.newDeployResourceCommand()).thenReturn(mockDeployCommandStep1);
    when(mockDeployCommandStep1.addResourceFromClasspath(anyString()))
        .thenThrow(new RuntimeException("Resource not found"));
    final Method testMethod =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");
    final Class<?> testClass = TestClassWithMethodAnnotation.class;

    // then
    assertThatThrownBy(
            () -> testDeploymentService.deployTestResources(testMethod, testClass, mockClient))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to deploy test resources")
        .hasMessageContaining("testMethodWithDeployment");
  }

  @Test
  void shouldHandleNullClient() throws Exception {
    // given
    final Method testMethod =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");
    final Class<?> testClass = TestClassWithMethodAnnotation.class;

    // when/then
    assertThatThrownBy(
            () -> testDeploymentService.deployTestResources(testMethod, testClass, mockClient))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to deploy test resources")
        .hasMessageContaining("testMethodWithDeployment");
  }

  @Test
  void shouldHandleNullMethod() throws Exception {
    // given
    final Class<?> testClass = TestClassWithMethodAnnotation.class;

    // when
    testDeploymentService.deployTestResources(null, testClass, mockClient);

    // then
    verify(mockClient, never()).newDeployResourceCommand();
  }

  @Test
  void shouldHandleNullTestClass() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithMethodAnnotation.class.getDeclaredMethod("testMethodWithDeployment");

    // when
    testDeploymentService.deployTestResources(testMethod, null, mockClient);

    // then
    verify(mockClient, times(1)).newDeployResourceCommand();
  }

  @Test
  void shouldHandleResourcesWithDifferentPaths() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithResourcePaths.class.getDeclaredMethod("testMethodWithResourcePaths");
    final Class<?> testClass = TestClassWithResourcePaths.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    verify(mockDeployCommandStep1).addResourceFromClasspath("coverage/process.bpmn");
    verify(mockDeployCommandStep1).addResourceFromClasspath("decisions/decision.dmn");
    verify(mockDeployCommandStep1).addResourceFromClasspath("forms/form.form");
    verify(mockDeployCommandStep2, times(3)).send();
  }

  @Test
  void shouldHandleLargeNumberOfResources() throws Exception {
    // given
    setupDeploymentMocks();
    final Method testMethod =
        TestClassWithManyResources.class.getDeclaredMethod("testMethodWithManyResources");
    final Class<?> testClass = TestClassWithManyResources.class;

    // when
    testDeploymentService.deployTestResources(testMethod, testClass, mockClient);

    // then
    // Verify all 10 resources are added
    for (int i = 1; i <= 10; i++) {
      verify(mockDeployCommandStep1).addResourceFromClasspath("process" + i + ".bpmn");
    }
    verify(mockDeployCommandStep2, times(10)).send();
  }

  // Test helper classes
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

  static class TestClassWithManyResources {
    @TestDeployment(
        resources = {
          "process1.bpmn",
          "process2.bpmn",
          "process3.bpmn",
          "process4.bpmn",
          "process5.bpmn",
          "process6.bpmn",
          "process7.bpmn",
          "process8.bpmn",
          "process9.bpmn",
          "process10.bpmn"
        })
    void testMethodWithManyResources() {}
  }
}
