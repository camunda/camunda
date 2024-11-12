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
package io.camunda.zeebe.spring.client.bean.factory;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.zeebe.spring.client.annotation.processor.ZeebeWorkerAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import io.camunda.zeebe.spring.client.bean.ClassInfoTest;
import io.camunda.zeebe.spring.client.bean.MethodInfo;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ReadZeebeWorkerValueTest {

  @Test
  public void applyOnWithZeebeWorker() {
    // given
    final ZeebeWorkerAnnotationProcessor annotationProcessor = createDefaultAnnotationProcessor();
    final MethodInfo methodInfo = extract(ClassInfoTest.WithZeebeWorker.class);

    // when
    final Optional<ZeebeWorkerValue> zeebeWorkerValue =
        annotationProcessor.readJobWorkerAnnotationForMethod(methodInfo);

    // then
    assertTrue(zeebeWorkerValue.isPresent());
    assertEquals("bar", zeebeWorkerValue.get().getType());
    assertEquals("kermit", zeebeWorkerValue.get().getName());
    assertEquals(Duration.ofMillis(100), zeebeWorkerValue.get().getTimeout());
    assertEquals(-1, zeebeWorkerValue.get().getMaxJobsActive());
    assertEquals(Duration.ofSeconds(-1), zeebeWorkerValue.get().getRequestTimeout());
    assertEquals(Duration.ofMillis(-1), zeebeWorkerValue.get().getPollInterval());
    assertEquals(false, zeebeWorkerValue.get().getAutoComplete());
    assertEquals(List.of(), zeebeWorkerValue.get().getFetchVariables());
    assertEquals(methodInfo, zeebeWorkerValue.get().getMethodInfo());
    assertEquals(Duration.ofHours(1), zeebeWorkerValue.get().getStreamTimeout());
  }

  @Test
  void shouldReadTenantIds() {
    // given
    final ZeebeWorkerAnnotationProcessor annotationProcessor = createDefaultAnnotationProcessor();
    final MethodInfo methodInfo = extract(ClassInfoTest.TenantBound.class);

    // when
    final Optional<ZeebeWorkerValue> zeebeWorkerValue =
        annotationProcessor.readJobWorkerAnnotationForMethod(methodInfo);

    // then
    assertTrue(zeebeWorkerValue.isPresent());
    assertThat(zeebeWorkerValue.get().getTenantIds()).containsOnly("tenant-1");
  }

  @Test
  public void applyOnWithZeebeWorkerAllValues() {
    // given
    final ZeebeWorkerAnnotationProcessor annotationProcessor = createDefaultAnnotationProcessor();
    final MethodInfo methodInfo = extract(ClassInfoTest.WithZeebeWorkerAllValues.class);

    // when
    final Optional<ZeebeWorkerValue> zeebeWorkerValue =
        annotationProcessor.readJobWorkerAnnotationForMethod(methodInfo);

    // then
    assertTrue(zeebeWorkerValue.isPresent());
    assertEquals("bar", zeebeWorkerValue.get().getType());
    assertEquals("kermit", zeebeWorkerValue.get().getName());
    assertEquals(Duration.ofMillis(100L), zeebeWorkerValue.get().getTimeout());
    assertEquals(3, zeebeWorkerValue.get().getMaxJobsActive());
    assertEquals(Duration.ofSeconds(500L), zeebeWorkerValue.get().getRequestTimeout());
    assertEquals(Duration.ofSeconds(1L), zeebeWorkerValue.get().getPollInterval());
    assertEquals(true, zeebeWorkerValue.get().getAutoComplete());
    assertEquals(List.of("foo"), zeebeWorkerValue.get().getFetchVariables());
    assertEquals(methodInfo, zeebeWorkerValue.get().getMethodInfo());
  }

  private ZeebeWorkerAnnotationProcessor createDefaultAnnotationProcessor() {
    return new ZeebeWorkerAnnotationProcessor(null, new ArrayList<>());
  }

  private MethodInfo extract(final Class<?> clazz) {

    final Method method =
        Arrays.stream(clazz.getMethods())
            .filter(m -> m.getName().equals("handle"))
            .findFirst()
            .get();
    final ClassInfo classInfo = ClassInfo.builder().build();
    return MethodInfo.builder().classInfo(classInfo).method(method).build();
  }
}
