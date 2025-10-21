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
package io.camunda.client.spring.bean.factory;

import static io.camunda.client.annotation.AnnotationUtil.getDeploymentValues;
import static io.camunda.client.spring.testsupport.BeanInfoUtil.beanInfo;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.Deployment;
import io.camunda.client.annotation.value.DeploymentValue;
import io.camunda.client.bean.BeanInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReadDeploymentValueTest {

  @Test
  public void shouldReadSingleClassPathResourceTest() {
    // given
    final BeanInfo classInfo = beanInfo(new WithSingleClassPathResource());

    final DeploymentValue expectedDeploymentValue =
        new DeploymentValue(Collections.singletonList("classpath*:/1.bpmn"), null);

    // when
    final List<DeploymentValue> valueForClass = getDeploymentValues(classInfo);

    // then
    assertThat(valueForClass).hasSize(1);
    assertThat(valueForClass.get(0)).isEqualTo(expectedDeploymentValue);
  }

  @Test
  public void shouldReadMultipleClassPathResourcesTest() {
    // given
    final BeanInfo classInfo = beanInfo(new WithMultipleClassPathResource());

    final DeploymentValue expectedDeploymentValue =
        new DeploymentValue(Arrays.asList("classpath*:/1.bpmn", "classpath*:/2.bpmn"), null);

    // when
    final List<DeploymentValue> valueForClass = getDeploymentValues(classInfo);

    // then
    assertThat(valueForClass).hasSize(1);
    assertThat(valueForClass.get(0)).isEqualTo(expectedDeploymentValue);
  }

  @Test
  public void shouldReadNoClassPathResourcesTest() {
    // given
    final BeanInfo classInfo = beanInfo(new WithoutAnnotation());

    // when
    final List<DeploymentValue> valueForClass = getDeploymentValues(classInfo);

    // then
    assertThat(valueForClass).isEmpty();
  }

  @Test
  public void shouldReadSingleClassPathResourceTestLegacy() {
    // given
    final BeanInfo classInfo = beanInfo(new WithSingleClassPathResourceLegacy());
    final DeploymentValue expectedDeploymentValue =
        new DeploymentValue(Collections.singletonList("classpath*:/1.bpmn"), null);

    // when
    final List<DeploymentValue> valueForClass = getDeploymentValues(classInfo);

    // then
    assertThat(valueForClass).hasSize(1);
    assertThat(valueForClass.get(0)).isEqualTo(expectedDeploymentValue);
  }

  @Test
  public void shouldReadMultipleClassPathResourcesTestLegacy() {
    // given
    final BeanInfo classInfo = beanInfo(new WithMultipleClassPathResourceLegacy());
    final DeploymentValue expectedDeploymentValue =
        new DeploymentValue(Arrays.asList("classpath*:/1.bpmn", "classpath*:/2.bpmn"), null);

    // when
    final List<DeploymentValue> valueForClass = getDeploymentValues(classInfo);

    // then
    assertThat(valueForClass).hasSize(1);
    assertThat(valueForClass.get(0)).isEqualTo(expectedDeploymentValue);
  }

  @Deployment(resources = "classpath*:/1.bpmn")
  private static final class WithSingleClassPathResource {}

  @Deployment(resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static final class WithMultipleClassPathResource {}

  @io.camunda.zeebe.spring.client.annotation.Deployment(resources = "classpath*:/1.bpmn")
  private static final class WithSingleClassPathResourceLegacy {}

  @io.camunda.zeebe.spring.client.annotation.Deployment(
      resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static final class WithMultipleClassPathResourceLegacy {}

  private static final class WithoutAnnotation {}
}
