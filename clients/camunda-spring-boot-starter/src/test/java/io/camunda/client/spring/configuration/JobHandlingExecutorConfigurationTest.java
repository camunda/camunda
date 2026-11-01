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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JobHandlingExecutorConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class));

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void shouldRunJobHandlersOnVirtualThreadsWhenExecutionThreadsNotSet() {
    contextRunner.run(
        context -> {
          // given
          final CamundaClientExecutorService executors =
              context.getBean(CamundaClientExecutorService.class);

          // when
          final Thread handlerThread = threadOf(executors.getJobHandlingExecutor());

          // then
          assertThat(isVirtual(handlerThread)).isTrue();
          assertThat(isVirtual(threadOf(executors.getScheduledExecutor()))).isFalse();
          assertThat(executors.isJobHandlingExecutorOwnedByCamundaClient()).isTrue();
          assertThat(executors.isScheduledExecutorOwnedByCamundaClient()).isTrue();
          assertThat(
                  context
                      .getBean(CamundaClientConfiguration.class)
                      .getNumJobWorkerExecutionThreads())
              .isOne();
        });
  }

  @Test
  void shouldShareFixedPlatformThreadPoolWhenExecutionThreadsSet() {
    contextRunner
        .withPropertyValues("camunda.client.execution-threads=3")
        .run(
            context -> {
              // given
              final CamundaClientExecutorService executors =
                  context.getBean(CamundaClientExecutorService.class);

              // then
              assertThat(executors.getJobHandlingExecutor())
                  .isSameAs(executors.getScheduledExecutor());
              assertThat(executors.getScheduledExecutor())
                  .isInstanceOfSatisfying(
                      ScheduledThreadPoolExecutor.class,
                      pool -> assertThat(pool.getCorePoolSize()).isEqualTo(3));
              assertThat(
                      context
                          .getBean(CamundaClientConfiguration.class)
                          .getNumJobWorkerExecutionThreads())
                  .isEqualTo(3);
            });
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void shouldApplyExecutionThreadsPerClient() {
    contextRunner
        .withPropertyValues(
            "camunda.clients.fixed.execution-threads=2",
            "camunda.clients.fixed.physical-tenant-id=fixed",
            "camunda.clients.unset.physical-tenant-id=unset")
        .run(
            context -> {
              // given
              final CamundaClientConfiguration fixed =
                  context.getBean("fixedCamundaClient", CamundaClient.class).getConfiguration();
              final CamundaClientConfiguration unset =
                  context.getBean("unsetCamundaClient", CamundaClient.class).getConfiguration();

              // then
              assertThat(fixed.jobHandlingExecutor())
                  .isSameAs(fixed.jobWorkerSchedulingExecutor())
                  .isInstanceOfSatisfying(
                      ScheduledThreadPoolExecutor.class,
                      pool -> assertThat(pool.getCorePoolSize()).isEqualTo(2));
              assertThat(isVirtual(threadOf(unset.jobHandlingExecutor()))).isTrue();
            });
  }

  private static Thread threadOf(final ExecutorService executor) throws Exception {
    return executor.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
  }

  private static boolean isVirtual(final Thread thread) throws ReflectiveOperationException {
    return (Boolean) Thread.class.getMethod("isVirtual").invoke(thread);
  }
}
