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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.client.spring.configuration.CamundaClientFactory;
import io.camunda.client.spring.configuration.CredentialsProviderConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests the {@code camunda.client.threads.virtual.enabled} property switch on {@link
 * VirtualThreadsAutoConfiguration}: when enabled (the default), the shared {@link
 * CamundaClientExecutorService} runs jobs on virtual threads; when disabled, the default
 * platform-thread executor from {@code camunda-spring-boot-starter} (sized by {@code
 * camunda.client.execution-threads}) takes over.
 */
class VirtualThreadsPropertyTest {

  private static final String VIRTUAL_THREADS_PROPERTY = "camunda.client.threads.virtual.enabled";

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                VirtualThreadsAutoConfiguration.class, CamundaAutoConfiguration.class));
  }

  private static void assertJobHandlingRunsOnVirtualThread(final CamundaClient client)
      throws InterruptedException {
    final AtomicReference<Thread> capturedThread = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    client
        .getConfiguration()
        .jobHandlingExecutor()
        .execute(
            () -> {
              capturedThread.set(Thread.currentThread());
              latch.countDown();
            });

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(capturedThread.get()).isNotNull();
    assertThat(capturedThread.get().isVirtual()).isTrue();
    assertThat(capturedThread.get().getName()).startsWith("job-worker-virtual-");
  }

  private static void assertJobHandlingRunsOnPlatformThreads(
      final CamundaClient client, final int expectedPoolSize) throws InterruptedException {
    final AtomicReference<Thread> capturedThread = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    client
        .getConfiguration()
        .jobHandlingExecutor()
        .execute(
            () -> {
              capturedThread.set(Thread.currentThread());
              latch.countDown();
            });

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(capturedThread.get()).isNotNull();
    assertThat(capturedThread.get().isVirtual()).isFalse();

    final ExecutorService jobHandlingExecutor = client.getConfiguration().jobHandlingExecutor();
    assertThat(jobHandlingExecutor)
        .as("default executor is a platform-thread pool")
        .isInstanceOf(ThreadPoolExecutor.class);
    assertThat(((ThreadPoolExecutor) jobHandlingExecutor).getCorePoolSize())
        .as("pool sized by camunda.client.execution-threads")
        .isEqualTo(expectedPoolSize);
  }

  @Nested
  class WithoutMeterRegistry {

    @Test
    void shouldUseVirtualThreadsWhenPropertyAbsent() {
      contextRunner()
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                final CamundaClientExecutorService executorService =
                    context.getBean(CamundaClientExecutorService.class);
                assertThat(executorService)
                    .isNotInstanceOf(MeteredVirtualThreadCamundaClientExecutorService.class);
                assertJobHandlingRunsOnVirtualThread(context.getBean(CamundaClient.class));
              });
    }

    @Test
    void shouldUseVirtualThreadsWhenPropertyTrue() {
      contextRunner()
          .withPropertyValues(VIRTUAL_THREADS_PROPERTY + "=true")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                assertJobHandlingRunsOnVirtualThread(context.getBean(CamundaClient.class));
              });
    }

    @Test
    void shouldUsePlatformThreadsWhenPropertyFalse() {
      contextRunner()
          .withPropertyValues(VIRTUAL_THREADS_PROPERTY + "=false")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                final CamundaClientExecutorService executorService =
                    context.getBean(CamundaClientExecutorService.class);
                assertThat(executorService)
                    .isNotInstanceOf(MeteredVirtualThreadCamundaClientExecutorService.class);
                assertJobHandlingRunsOnPlatformThreads(context.getBean(CamundaClient.class), 1);
              });
    }

    @Test
    void shouldRespectExecutionThreadsWhenPropertyFalse() {
      contextRunner()
          .withPropertyValues(
              VIRTUAL_THREADS_PROPERTY + "=false", "camunda.client.execution-threads=5")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                assertJobHandlingRunsOnPlatformThreads(context.getBean(CamundaClient.class), 5);
              });
    }
  }

  @Nested
  class WithMeterRegistry {

    @Test
    void shouldUseMeteredVirtualThreadsWhenPropertyAbsent() {
      contextRunner()
          .withBean(SimpleMeterRegistry.class)
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                final CamundaClientExecutorService executorService =
                    context.getBean(CamundaClientExecutorService.class);
                assertThat(executorService)
                    .isInstanceOf(MeteredVirtualThreadCamundaClientExecutorService.class);
                assertJobHandlingRunsOnVirtualThread(context.getBean(CamundaClient.class));
              });
    }

    @Test
    void shouldUseMeteredVirtualThreadsWhenPropertyTrue() {
      contextRunner()
          .withBean(SimpleMeterRegistry.class)
          .withPropertyValues(VIRTUAL_THREADS_PROPERTY + "=true")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                final CamundaClientExecutorService executorService =
                    context.getBean(CamundaClientExecutorService.class);
                assertThat(executorService)
                    .isInstanceOf(MeteredVirtualThreadCamundaClientExecutorService.class);
                assertJobHandlingRunsOnVirtualThread(context.getBean(CamundaClient.class));
              });
    }

    @Test
    void shouldUsePlatformThreadsWhenPropertyFalse() {
      contextRunner()
          .withBean(SimpleMeterRegistry.class)
          .withPropertyValues(VIRTUAL_THREADS_PROPERTY + "=false")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                assertJobHandlingRunsOnPlatformThreads(context.getBean(CamundaClient.class), 1);
              });
    }

    @Test
    void shouldRespectExecutionThreadsWhenPropertyFalse() {
      contextRunner()
          .withBean(SimpleMeterRegistry.class)
          .withPropertyValues(
              VIRTUAL_THREADS_PROPERTY + "=false", "camunda.client.execution-threads=3")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                assertJobHandlingRunsOnPlatformThreads(context.getBean(CamundaClient.class), 3);
              });
    }
  }

  @Nested
  class WithMultipleNamedClients {

    @Test
    void shouldApplyVirtualThreadsToNamedClientsWhenPropertyAbsent() {
      contextRunner()
          .withPropertyValues(
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                assertThat(context).hasBean("financeCamundaClient");
                assertThat(context).hasBean("riskCamundaClient");
                assertJobHandlingRunsOnVirtualThread(
                    context.getBean("financeCamundaClient", CamundaClient.class));
                assertJobHandlingRunsOnVirtualThread(
                    context.getBean("riskCamundaClient", CamundaClient.class));
              });
    }

    @Test
    void shouldApplyVirtualThreadsToNamedClientsWhenPropertyTrue() {
      contextRunner()
          .withPropertyValues(
              VIRTUAL_THREADS_PROPERTY + "=true",
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                assertThat(context.getBeansOfType(CamundaClient.class)).hasSize(2);
                assertJobHandlingRunsOnVirtualThread(
                    context.getBean("financeCamundaClient", CamundaClient.class));
                assertJobHandlingRunsOnVirtualThread(
                    context.getBean("riskCamundaClient", CamundaClient.class));
              });
    }

    @Test
    void shouldUsePlatformThreadsForNamedClientsWhenPropertyFalse() {
      contextRunner()
          .withPropertyValues(
              VIRTUAL_THREADS_PROPERTY + "=false",
              "camunda.client.execution-threads=4",
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                assertThat(context.getBeansOfType(CamundaClient.class)).hasSize(2);
                assertJobHandlingRunsOnPlatformThreads(
                    context.getBean("financeCamundaClient", CamundaClient.class), 4);
                assertJobHandlingRunsOnPlatformThreads(
                    context.getBean("riskCamundaClient", CamundaClient.class), 4);
              });
    }

    @Test
    void shouldApplyVirtualThreadsToNamedClientsWhenMetered() {
      contextRunner()
          .withBean(SimpleMeterRegistry.class)
          .withPropertyValues(
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                assertThat(context.getBeansOfType(CamundaClient.class)).hasSize(2);
                // the shared bean is metered, but per-client executors deliberately are not:
                // metering is Micrometer's concern, Micrometer is optional, and the multi-client
                // factory path must not depend on it — see CamundaClientFactory
                assertThat(context.getBean(CamundaClientExecutorService.class))
                    .isInstanceOf(MeteredVirtualThreadCamundaClientExecutorService.class);
                assertThat(
                        context
                            .getBean("financeCamundaClient", CamundaClient.class)
                            .getConfiguration()
                            .jobHandlingExecutor()
                            .getClass()
                            .getName())
                    .as("per-client executors are not wrapped in Micrometer's timed decorator")
                    .doesNotContain("micrometer");
                assertJobHandlingRunsOnVirtualThread(
                    context.getBean("financeCamundaClient", CamundaClient.class));
                assertJobHandlingRunsOnVirtualThread(
                    context.getBean("riskCamundaClient", CamundaClient.class));
              });
    }

    @Test
    void shouldUsePlatformThreadsForNamedClientsWithMeterRegistryWhenPropertyFalse() {
      contextRunner()
          .withBean(SimpleMeterRegistry.class)
          .withPropertyValues(
              VIRTUAL_THREADS_PROPERTY + "=false",
              "camunda.client.execution-threads=3",
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                assertThat(context.getBeansOfType(CamundaClient.class)).hasSize(2);
                assertJobHandlingRunsOnPlatformThreads(
                    context.getBean("financeCamundaClient", CamundaClient.class), 3);
                assertJobHandlingRunsOnPlatformThreads(
                    context.getBean("riskCamundaClient", CamundaClient.class), 3);
              });
    }

    @Test
    void shouldGiveEachNamedClientItsOwnedVirtualThreadExecutor() {
      contextRunner()
          .withPropertyValues(
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                // the shared executor bean is closed by the context, not by any client, so each
                // named client must own a distinct executor — otherwise one client's close would
                // silently stop job handling for the others
                final ExecutorService financeExecutor =
                    context
                        .getBean("financeCamundaClient", CamundaClient.class)
                        .getConfiguration()
                        .jobHandlingExecutor();
                final ExecutorService riskExecutor =
                    context
                        .getBean("riskCamundaClient", CamundaClient.class)
                        .getConfiguration()
                        .jobHandlingExecutor();
                assertThat(financeExecutor).isNotSameAs(riskExecutor);
                assertThat(financeExecutor)
                    .isNotSameAs(
                        context
                            .getBean(CamundaClientExecutorService.class)
                            .getJobHandlingExecutor());
              });
    }

    @Test
    void shouldWireSharedExecutorIntoClientConfiguration() {
      contextRunner()
          .withPropertyValues(
              "camunda.clients.finance.physical-tenant-id=finance",
              "camunda.clients.finance.primary=true",
              "camunda.clients.risk.physical-tenant-id=risk")
          .run(
              context -> {
                assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
                final CamundaClientExecutorService sharedExecutor =
                    context.getBean(CamundaClientExecutorService.class);
                final CamundaClientConfiguration configuration =
                    context.getBean(CamundaClientConfiguration.class);
                assertThat(configuration.jobHandlingExecutor())
                    .as("primary client reuses the shared (virtual-thread) executor")
                    .isSameAs(sharedExecutor.getJobHandlingExecutor());
              });
    }

    /**
     * Regression test for the class-name misclassification: a user-supplied shared executor built
     * on a <em>platform</em> thread-per-task factory is also a JDK {@code ThreadPerTaskExecutor},
     * so sniffing the executor's class name reads it as virtual. The factory must instead honour
     * the explicit {@link io.camunda.client.jobhandling.VirtualThreadExecutorService} capability,
     * which this custom bean does not implement — so the owned executor it builds is a
     * platform-thread pool, not a virtual-thread executor.
     */
    @Test
    void shouldNotTreatPlatformThreadPerTaskSharedExecutorAsVirtual() {
      // given a shared executor bean whose job-handling executor is a platform thread-per-task
      // executor (the shape the old class-name check misread as virtual)
      final CamundaClientExecutorService platformPerTaskShared =
          new CamundaClientExecutorService(
              Executors.newScheduledThreadPool(1),
              true,
              Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory()),
              true);
      final CamundaClientFactory factory =
          new CamundaClientFactory(
              new CredentialsProviderConfiguration(),
              emptyProvider(),
              List.of(),
              List.of(),
              providerOf(platformPerTaskShared),
              emptyProvider());
      final CamundaClientProperties properties = new CamundaClientProperties();
      properties.setExecutionThreads(2);

      // when the factory builds an owned executor for a named client
      final CamundaClient client = factory.createClient("finance", properties, false);

      // then the owned executor is a platform-thread pool sized by execution-threads, not virtual
      final ExecutorService owned = client.getConfiguration().jobHandlingExecutor();
      assertThat(owned)
          .as("a platform thread-per-task shared bean must not yield a virtual executor")
          .isInstanceOf(ThreadPoolExecutor.class);
      assertThat(((ThreadPoolExecutor) owned).getCorePoolSize()).isEqualTo(2);
    }

    private static <T> ObjectProvider<T> providerOf(final T bean) {
      return new ObjectProvider<>() {
        @Override
        public T getObject() {
          return bean;
        }
      };
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
      return (ObjectProvider<T>) providerOf(null);
    }
  }
}
