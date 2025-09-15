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

import static io.camunda.client.spring.testsupport.BeanInfoUtil.beanInfo;
import static org.assertj.core.api.Assertions.*;

import io.camunda.client.annotation.AnnotationUtil;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.spring.bean.SpringBeanInfoTest;
import io.camunda.client.spring.bean.SpringBeanInfoTest.WithJobWorker;
import io.camunda.client.spring.bean.SpringBeanInfoTest.WithJobWorkerAllValues;
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
    assertThat(jobWorkerValue.isPresent()).isTrue();
    assertThat(jobWorkerValue.get().getType()).isEqualTo("bar");
    assertThat(jobWorkerValue.get().getName()).isEqualTo("kermit");
    assertThat(jobWorkerValue.get().getTimeout()).isEqualTo(Duration.ofMillis(100));
    assertThat(jobWorkerValue.get().getMaxJobsActive()).isEqualTo(-1);
    assertThat(jobWorkerValue.get().getRequestTimeout()).isEqualTo(Duration.ofSeconds(-1));
    assertThat(jobWorkerValue.get().getPollInterval()).isEqualTo(Duration.ofMillis(-1));
    assertThat(jobWorkerValue.get().getAutoComplete()).isEqualTo(false);
    assertThat(jobWorkerValue.get().getFetchVariables()).isEqualTo(List.of());
    assertThat(jobWorkerValue.get().getMethodInfo()).isEqualTo(methodInfo);
    assertThat(jobWorkerValue.get().getStreamTimeout()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void shouldReadTenantIds() {
    // given
    final MethodInfo methodInfo = extract(SpringBeanInfoTest.TenantBound.class);

    // when
    final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);

    // then
    assertThat(jobWorkerValue.isPresent()).isTrue();
    assertThat(jobWorkerValue.get().getTenantIds()).containsOnly("tenant-1");
  }

  @Test
  public void applyOnWithJobWorkerAllValues() {
    // given
    final MethodInfo methodInfo = extract(WithJobWorkerAllValues.class);

    // when
    final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);

    // then
    assertThat(jobWorkerValue.isPresent()).isTrue();
    assertThat(jobWorkerValue.get().getType()).isEqualTo("bar");
    assertThat(jobWorkerValue.get().getName()).isEqualTo("kermit");
    assertThat(jobWorkerValue.get().getTimeout()).isEqualTo(Duration.ofMillis(100L));
    assertThat(jobWorkerValue.get().getMaxJobsActive()).isEqualTo(3);
    assertThat(jobWorkerValue.get().getRequestTimeout()).isEqualTo(Duration.ofSeconds(500L));
    assertThat(jobWorkerValue.get().getPollInterval()).isEqualTo(Duration.ofSeconds(1L));
    assertThat(jobWorkerValue.get().getAutoComplete()).isEqualTo(true);
    assertThat(jobWorkerValue.get().getFetchVariables()).isEqualTo(List.of("foo"));
    assertThat(jobWorkerValue.get().getMethodInfo()).isEqualTo(methodInfo);
  }

  private MethodInfo extract(final Class<?> clazz) {

    final Method method =
        Arrays.stream(clazz.getMethods())
            .filter(m -> m.getName().equals("handle"))
            .findFirst()
            .get();
    final BeanInfo classInfo = beanInfo(clazz);
    return MethodInfo.builder().beanInfo(classInfo).method(method).build();
  }
}
