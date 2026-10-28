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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.JobExceptionHandlerSupplier;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Builds a stand-alone {@link CamundaClient} from a single resolved {@link CamundaClientProperties}
 * entry, used by {@link MultiCamundaClientBeanDefinitionRegistryPostProcessor} to create one client
 * per configured name.
 *
 * <p>The property mapping is delegated to {@link SpringCamundaClientConfiguration} — the same
 * adapter the single-client path uses — so every {@link CamundaClientProperties} field (including
 * the {@code physical-tenant-id} / {@code prefix-physical-tenant-path} scope) maps through one
 * authoritative place and cannot drift as new client properties are added.
 *
 * <p>Collaborators that exist as beans in the context are reused: the per-client {@link
 * CredentialsProvider} is derived from the entry's {@code auth.*} via {@link
 * CredentialsProviderConfiguration}, and any user-defined {@link JsonMapper}, {@link
 * ClientInterceptor}s, {@link AsyncExecChainHandler}s, {@link CamundaClientExecutorService} and
 * {@link JobExceptionHandlerSupplier} are applied (falling back to self-contained defaults when
 * absent), so overrides such as {@code VirtualThreadsAutoConfiguration}'s executor take effect.
 *
 * <p>Executor sizing follows the client count: the sole (default) client reuses the shared context
 * executor bean (so overrides like {@code VirtualThreadsAutoConfiguration} apply and it matches the
 * former single-client path); with several named clients, each gets its own owned executor, so one
 * client's shutdown cannot stop another's. The per-client executor preserves the shared bean's
 * execution mode: a virtual-thread shared bean (from {@code VirtualThreadsAutoConfiguration})
 * yields per-client virtual-thread executors, anything else yields the default platform-thread pool
 * sized by the client's {@code execution-threads}.
 */
public class CamundaClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientFactory.class);

  private final CredentialsProviderConfiguration credentialsProviderConfiguration;
  private final ObjectProvider<JsonMapper> jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final ObjectProvider<CamundaClientExecutorService> executorService;
  private final ObjectProvider<JobExceptionHandlerSupplier> jobExceptionHandlerSupplier;
  private final ObjectProvider<MeterRegistry> meterRegistry;

  public CamundaClientFactory(
      final CredentialsProviderConfiguration credentialsProviderConfiguration,
      final ObjectProvider<JsonMapper> jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ObjectProvider<CamundaClientExecutorService> executorService,
      final ObjectProvider<JobExceptionHandlerSupplier> jobExceptionHandlerSupplier,
      final ObjectProvider<MeterRegistry> meterRegistry) {
    this.credentialsProviderConfiguration = credentialsProviderConfiguration;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.executorService = executorService;
    this.jobExceptionHandlerSupplier = jobExceptionHandlerSupplier;
    this.meterRegistry = meterRegistry;
  }

  public CamundaClient createClient(
      final String name,
      final CamundaClientProperties properties,
      final boolean useSharedExecutor) {
    LOG.debug("Creating CamundaClient '{}'", name);
    final CredentialsProvider credentialsProvider =
        credentialsProviderConfiguration.camundaClientCredentialsProvider(properties);

    // Reuse the single-client adapter for the full property mapping, wired with the context
    // collaborators. The json mapper falls back to the client default when no bean is defined; the
    // job-exception-handler prefers the context bean (so user overrides take effect), else default.
    //
    // Executor: for the sole (default) client, use the shared context executor bean so overrides
    // such as VirtualThreadsAutoConfiguration's executor take effect. When several named clients
    // are configured, each gets its own owned executor, so one client's shutdown cannot stop
    // another's and per-client sizing is honored.
    final CamundaClientExecutorService executor =
        useSharedExecutor
            ? executorService.getIfAvailable(
                () -> CamundaClientExecutorService.createDefault(executionThreads(properties)))
            : createOwnedExecutor(properties);
    final CamundaClientConfiguration configuration =
        new SpringCamundaClientConfiguration(
            properties,
            jsonMapper.getIfAvailable(),
            interceptors,
            chainHandlers,
            executor,
            credentialsProvider,
            jobExceptionHandlerSupplier.getIfAvailable(
                () -> context -> JobExceptionHandler.createDefault()));

    return CamundaClient.newClientBuilder()
        .withConfiguration(configuration)
        // the resolved Spring properties are authoritative; don't let client-level environment
        // variables override them (matching the single-client path)
        .applyEnvironmentVariableOverrides(false)
        .build();
  }

  /**
   * Builds the owned executor for one of several named clients. A shared executor bean that runs
   * jobs on virtual threads (the {@code VirtualThreadsAutoConfiguration} override) is replicated
   * per client — one virtual-thread executor cannot be shared, because it is owned by its client
   * and closed with it, which would silently stop job handling for every other client. Otherwise
   * the default platform-thread pool applies, sized by the client's {@code execution-threads}.
   */
  private CamundaClientExecutorService createOwnedExecutor(
      final CamundaClientProperties properties) {
    final CamundaClientExecutorService shared = executorService.getIfAvailable();
    if (shared == null || !runsOnVirtualThreads(shared)) {
      return CamundaClientExecutorService.createDefault(executionThreads(properties));
    }
    final ExecutorService jobHandlingExecutor = newVirtualThreadExecutor();
    final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    final MeterRegistry registry = meterRegistry.getIfAvailable();
    return registry == null
        ? new CamundaClientExecutorService(scheduledExecutor, true, jobHandlingExecutor, true)
        : new MeteredCamundaClientExecutorService(
            scheduledExecutor, true, jobHandlingExecutor, true, registry);
  }

  private static int executionThreads(final CamundaClientProperties properties) {
    final Integer executionThreads = properties.getExecutionThreads();
    // mirrors CamundaClientBuilderImpl's default; the property is only null when the Binder never
    // touched the instance (e.g. tests constructing properties manually)
    return executionThreads == null ? 1 : executionThreads;
  }

  /**
   * The starter still compiles at Java 17 for consumers on older Spring Boot versions, so the
   * virtual-thread executor is built reflectively. It is only reached when the shared executor bean
   * already runs on virtual threads, which requires Java 21+ at runtime — hence the reflective
   * lookup cannot fail here.
   */
  private static ExecutorService newVirtualThreadExecutor() {
    try {
      // Thread.Builder.OfVirtual is a public nested interface, so methods looked up on it are
      // accessible even though the runtime implementation class is package-private
      final Class<?> builderType = Class.forName("java.lang.Thread$Builder$OfVirtual");
      final Object threadBuilder = Thread.class.getMethod("ofVirtual").invoke(null);
      final Object namedBuilder =
          builderType
              .getMethod("name", String.class, long.class)
              .invoke(threadBuilder, "job-worker-virtual-", 0);
      final ThreadFactory factory =
          (ThreadFactory) builderType.getMethod("factory").invoke(namedBuilder);
      return (ExecutorService)
          Executors.class
              .getMethod("newThreadPerTaskExecutor", ThreadFactory.class)
              .invoke(null, factory);
    } catch (final ReflectiveOperationException e) {
      // unreachable: called only when the shared executor already creates virtual threads
      throw new IllegalStateException("Virtual threads require Java 21+", e);
    }
  }

  /**
   * Detects a virtual-thread executor by running a probe task on it: a thread-per-task virtual
   * executor (the {@code VirtualThreadsAutoConfiguration} override) runs it on a virtual thread. A
   * probe is used instead of {@code ExecutorService#isVirtualThreadPerTaskExecutor()} because that
   * method only reports true when the executor was created by the JDK's virtual-thread factory
   * methods directly — a decorating executor (e.g. Micrometer's timed wrapper) answers false even
   * though every task still runs on a virtual thread.
   */
  private static boolean runsOnVirtualThreads(final CamundaClientExecutorService executorService) {
    final ExecutorService jobHandlingExecutor = executorService.getJobHandlingExecutor();
    if (jobHandlingExecutor == null) {
      return false;
    }
    try {
      return (boolean)
          jobHandlingExecutor
              .submit(
                  () ->
                      // Thread.isVirtual() is invoked reflectively: the starter compiles at Java
                      // 17, where the method does not exist — but a virtual-thread executor can
                      // only exist on Java 21+, where it does
                      Thread.class.getMethod("isVirtual").invoke(Thread.currentThread()))
              .get();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while inspecting the job-handling executor", e);
    } catch (final ExecutionException e) {
      throw new IllegalStateException("Failed to inspect the job-handling executor", e);
    }
  }
}
