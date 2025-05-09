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
package io.camunda.spring.client.bean.factory;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.spring.client.annotation.AnnotationUtil;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.bean.ClassInfoTest;
import io.camunda.spring.client.bean.ClassInfoTest.WithJobWorker;
import io.camunda.spring.client.bean.ClassInfoTest.WithJobWorkerAllValues;
import io.camunda.spring.client.bean.MethodInfo;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ReadJobWorkerValueTest {

  @Test
  public void applyOnWithJobWorker() {
    // given
    final MethodInfo methodInfo = extract(WithJobWorker.class);

    // when
    final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);

    // then
    assertTrue(jobWorkerValue.isPresent());
    assertEquals("bar", jobWorkerValue.get().getType());
    assertEquals("kermit", jobWorkerValue.get().getName());
    assertEquals(Duration.ofMillis(100), jobWorkerValue.get().getTimeout());
    assertEquals(-1, jobWorkerValue.get().getMaxJobsActive());
    assertEquals(Duration.ofSeconds(-1), jobWorkerValue.get().getRequestTimeout());
    assertEquals(Duration.ofMillis(-1), jobWorkerValue.get().getPollInterval());
    assertEquals(false, jobWorkerValue.get().getAutoComplete());
    assertEquals(List.of(), jobWorkerValue.get().getFetchVariables());
    assertEquals(methodInfo, jobWorkerValue.get().getMethodInfo());
    assertEquals(Duration.ofHours(1), jobWorkerValue.get().getStreamTimeout());
  }

  @Test
  void shouldReadTenantIds() {
    // given
    final MethodInfo methodInfo = extract(ClassInfoTest.TenantBound.class);

    // when
    final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);

    // then
    assertTrue(jobWorkerValue.isPresent());
    assertThat(jobWorkerValue.get().getTenantIds()).containsOnly("tenant-1");
  }

  @Test
  public void applyOnWithJobWorkerAllValues() {
    // given
    final MethodInfo methodInfo = extract(WithJobWorkerAllValues.class);

    // when
    final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);

    // then
    assertTrue(jobWorkerValue.isPresent());
    assertEquals("bar", jobWorkerValue.get().getType());
    assertEquals("kermit", jobWorkerValue.get().getName());
    assertEquals(Duration.ofMillis(100L), jobWorkerValue.get().getTimeout());
    assertEquals(3, jobWorkerValue.get().getMaxJobsActive());
    assertEquals(Duration.ofSeconds(500L), jobWorkerValue.get().getRequestTimeout());
    assertEquals(Duration.ofSeconds(1L), jobWorkerValue.get().getPollInterval());
    assertEquals(true, jobWorkerValue.get().getAutoComplete());
    assertEquals(List.of("foo"), jobWorkerValue.get().getFetchVariables());
    assertEquals(methodInfo, jobWorkerValue.get().getMethodInfo());
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
