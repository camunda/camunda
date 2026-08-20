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
package io.camunda.client.virtualthreads;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {VirtualThreadsAutoConfiguration.class, CamundaAutoConfiguration.class},
    properties = {"camunda.client.mode=selfManaged", "camunda.client.enabled=false"})
class VirtualThreadsAutoConfigurationIT {

  @Autowired private CamundaClientExecutorService executorService;

  @Test
  void shouldCreateCamundaClientExecutorServiceBean() {
    assertThat(executorService).isNotNull();
  }

  @Test
  void shouldUseVirtualThreadsForJobHandling() throws InterruptedException {
    final AtomicReference<Thread> capturedThread = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    executorService
        .getJobHandlingExecutor()
        .execute(
            () -> {
              capturedThread.set(Thread.currentThread());
              latch.countDown();
            });

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(capturedThread.get()).isNotNull();
    assertThat(capturedThread.get().isVirtual()).isTrue();
  }

  @Test
  void shouldUsePlatformThreadForScheduling() throws InterruptedException {
    final AtomicReference<Thread> capturedThread = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    executorService
        .getScheduledExecutor()
        .execute(
            () -> {
              capturedThread.set(Thread.currentThread());
              latch.countDown();
            });

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(capturedThread.get()).isNotNull();
    assertThat(capturedThread.get().isVirtual()).isFalse();
  }

  @Test
  void shouldHaveOwnedExecutors() {
    assertThat(executorService.isJobHandlingExecutorOwnedByCamundaClient()).isTrue();
    assertThat(executorService.isScheduledExecutorOwnedByCamundaClient()).isTrue();
  }
}
