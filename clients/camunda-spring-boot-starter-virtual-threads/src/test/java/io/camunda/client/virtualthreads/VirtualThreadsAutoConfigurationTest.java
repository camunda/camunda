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
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests that {@link VirtualThreadsAutoConfiguration} degrades gracefully when optional dependencies
 * (micrometer, actuator) are absent from the classpath. This is a regression test for Java 24+
 * compatibility where Spring Framework's ClassFileMetadataReader eagerly resolves class references.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/47464">#47464</a>
 */
class VirtualThreadsAutoConfigurationTest {

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(VirtualThreadsAutoConfiguration.class);
  }

  @Test
  void shouldCreateMeteredExecutorWhenBothMicrometerAndActuatorPresent() {
    // Both micrometer-core and spring-boot-actuator-autoconfigure are on the test classpath
    contextRunner()
        .withBean(SimpleMeterRegistry.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
              assertThat(context.getBean(CamundaClientExecutorService.class))
                  .isInstanceOf(MeteredCamundaClientExecutorService.class);
            });
  }

  @Test
  void shouldNotCreateMeteredExecutorWhenActuatorAbsent() {
    // micrometer is on test classpath, but actuator is filtered out — metered bean requires both
    contextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(MeteredCamundaClientExecutorService.class);
            });
  }

  @Test
  void shouldFallBackToUnmeteredExecutorWhenMicrometerAbsent() {
    contextRunner()
        .withClassLoader(new FilteredClassLoader("io.micrometer.core.instrument.MeterRegistry"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
              assertThat(context.getBean(CamundaClientExecutorService.class))
                  .isNotInstanceOf(MeteredCamundaClientExecutorService.class);
            });
  }

  @Test
  void shouldFallBackToUnmeteredExecutorWhenBothAbsent() {
    contextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                "io.micrometer.core.instrument.MeterRegistry",
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
              assertThat(context.getBean(CamundaClientExecutorService.class))
                  .isNotInstanceOf(MeteredCamundaClientExecutorService.class);
            });
  }

  /**
   * Verifies that {@code MeterRegistry} does not leak into the outer class as a method parameter
   * type. On Java 24+, {@link Class#getDeclaredMethods()} resolves all parameter types eagerly; if
   * {@code MeterRegistry} appeared as a method parameter on the outer class (instead of a nested
   * configuration class), Spring would throw {@link NoClassDefFoundError} when discovering
   * {@code @Bean} methods — before it even evaluates any {@code @ConditionalOnClass}.
   *
   * <p>This test builds a classloader that genuinely excludes micrometer JARs (unlike {@link
   * FilteredClassLoader}, which only intercepts {@link ClassLoader#loadClass} and cannot reproduce
   * the native {@link Class#getDeclaredMethods()} failure). It then loads the outer configuration
   * class and calls {@code getDeclaredMethods()}, which fails with {@link NoClassDefFoundError} if
   * the refactor is reverted.
   */
  @Test
  void shouldNotReferToMeterRegistryInOuterClassMethods() throws Exception {
    final String classpath = System.getProperty("java.class.path");
    final List<URL> urls = new ArrayList<>();
    for (final String entry : classpath.split(File.pathSeparator)) {
      if (!entry.contains("micrometer")) {
        urls.add(new File(entry).toURI().toURL());
      }
    }

    // Platform classloader skips the app classloader, so micrometer is truly absent
    try (URLClassLoader loader =
        new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
      final Class<?> outerClass =
          loader.loadClass("io.camunda.client.virtualthreads.VirtualThreadsAutoConfiguration");
      assertThatNoException()
          .as("getDeclaredMethods() must not throw NoClassDefFoundError for MeterRegistry")
          .isThrownBy(outerClass::getDeclaredMethods);
    }
  }
}
