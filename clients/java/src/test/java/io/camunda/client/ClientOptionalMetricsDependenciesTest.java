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
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.client.api.metrics.CamundaClientMetrics;
import io.camunda.client.api.response.StatusResponse.Status;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientCloudBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.http.HttpClientFactory;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.client.util.ClientRestTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Micrometer and the httpclient5 observation module are optional dependencies of the client. These
 * tests run the client in a class loader which hides them, to guard that the client works without
 * them as long as it does not need them.
 */
final class ClientOptionalMetricsDependenciesTest extends ClientRestTest {

  private static final String MICROMETER_PACKAGE = "io.micrometer.";
  private static final String HTTP_OBSERVATION_PACKAGE = "org.apache.hc.client5.http.observation.";

  private String restAddress;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo mockInfo) {
    restAddress = mockInfo.getHttpBaseUrl();
    gatewayService.onStatusRequestHealthy();
    gatewayService.onActivateJobsRequest(new JobActivationResult());
  }

  @Test
  void shouldUseClientWithoutMicrometer() throws Exception {
    // when
    final String status =
        runIsolated(
            WithoutMicrometer.class, restAddress, MICROMETER_PACKAGE, HTTP_OBSERVATION_PACKAGE);

    // then
    assertThat(status).isEqualTo(Status.UP.name());
  }

  @Test
  void shouldUseClientWithMicrometerMetricsButWithoutObservationModule() throws Exception {
    // when
    final String status =
        runIsolated(WithoutObservationModule.class, restAddress, HTTP_OBSERVATION_PACKAGE);

    // then
    assertThat(status).isEqualTo(Status.UP.name());
  }

  private static String runIsolated(
      final Class<? extends Function<String, String>> scenario,
      final String restAddress,
      final String... hiddenPackages)
      throws Exception {
    try (final URLClassLoader classLoader = new HidingClassLoader(hiddenPackages)) {
      @SuppressWarnings("unchecked")
      final Function<String, String> isolatedScenario =
          (Function<String, String>)
              classLoader.loadClass(scenario.getName()).getConstructor().newInstance();
      return isolatedScenario.apply(restAddress);
    }
  }

  /** Loaded and run in a class loader hiding Micrometer and the observation module. */
  public static final class WithoutMicrometer implements Function<String, String> {

    @Override
    public String apply(final String restAddress) {
      assertThat(Isolation.isHidden("io.micrometer.core.instrument.MeterRegistry", getClass()))
          .isTrue();

      // frameworks introspect the members of the types they manage or mock, e.g. Spring to find
      // injection points of the client bean, or Mockito the builder; this resolves all types in
      // their signatures. Mockito itself does not initialize in this class loader.
      for (final Class<?> type :
          new Class<?>[] {
            CamundaClientImpl.class,
            CamundaClientConfiguration.class,
            CamundaClientBuilder.class,
            CamundaClientBuilderImpl.class,
            CamundaClientCloudBuilderImpl.class,
            CamundaClientMetrics.class,
            JobWorkerBuilderImpl.class,
            HttpClientFactory.class
          }) {
        type.getDeclaredMethods();
        type.getDeclaredFields();
        type.getDeclaredConstructors();
        type.getMethods();
      }

      try (final CamundaClient client =
          CamundaClient.newClientBuilder()
              .preferRestOverGrpc(true)
              .restAddress(URI.create(restAddress))
              .build()) {
        try (final JobWorker ignored =
            client.newWorker().jobType("job").handler((c, j) -> {}).open()) {
          return client.newStatusRequest().send().join().getStatus().name();
        }
      }
    }
  }

  /** Loaded and run in a class loader hiding only the observation module. */
  public static final class WithoutObservationModule implements Function<String, String> {

    @Override
    public String apply(final String restAddress) {
      assertThat(
              Isolation.isHidden(
                  "org.apache.hc.client5.http.observation.HttpClientObservationSupport",
                  getClass()))
          .isTrue();

      final MeterRegistry meterRegistry = new SimpleMeterRegistry();
      try (final CamundaClient client =
          CamundaClient.newClientBuilder()
              .preferRestOverGrpc(true)
              .restAddress(URI.create(restAddress))
              .metrics(CamundaClientMetrics.micrometer().withMeterRegistry(meterRegistry).build())
              .build()) {
        try (final JobWorker ignored =
            client.newWorker().jobType("job").handler((c, j) -> {}).open()) {
          final String status = client.newStatusRequest().send().join().getStatus().name();

          assertThat(meterRegistry.find("http.client.pool.leased").gauge()).isNull();
          return status;
        }
      }
    }
  }

  /**
   * Helpers for the scenarios. Not on the test class itself, so that the scenarios do not load it
   * into the isolated class loader.
   */
  static final class Isolation {

    private Isolation() {}

    static boolean isHidden(final String className, final Class<?> scenario) {
      try {
        Class.forName(className, false, scenario.getClassLoader());
        return false;
      } catch (final ClassNotFoundException e) {
        return true;
      }
    }
  }

  /**
   * Loads all classes of the test class path itself, instead of delegating to the application class
   * loader, except those of the hidden packages, which it does not find at all.
   */
  private static final class HidingClassLoader extends URLClassLoader {

    private final String[] hiddenPackages;

    private HidingClassLoader(final String... hiddenPackages) {
      // the parent of the application class loader holds only the JDK classes
      super(testClassPath(), ClassLoader.getSystemClassLoader().getParent());
      this.hiddenPackages = hiddenPackages;
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
        throws ClassNotFoundException {
      for (final String hiddenPackage : hiddenPackages) {
        if (name.startsWith(hiddenPackage)) {
          throw new ClassNotFoundException(name);
        }
      }
      return super.loadClass(name, resolve);
    }

    private static URL[] testClassPath() {
      // surefire passes the class path in a manifest-only jar, but tells the actual one
      final String classPath =
          System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
      final List<URL> urls = new ArrayList<>();
      for (final String entry : classPath.split(File.pathSeparator)) {
        try {
          urls.add(new File(entry).toURI().toURL());
        } catch (final MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
      return urls.toArray(new URL[0]);
    }
  }
}
