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
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.util.List;
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
 * former single-client path); with several named clients, each gets its own owned executor sized by
 * its {@code execution-threads}, so one client's shutdown cannot stop another's.
 */
public class CamundaClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientFactory.class);

  private final CredentialsProviderConfiguration credentialsProviderConfiguration;
  private final ObjectProvider<JsonMapper> jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final ObjectProvider<CamundaClientExecutorService> executorService;
  private final ObjectProvider<JobExceptionHandlerSupplier> jobExceptionHandlerSupplier;

  public CamundaClientFactory(
      final CredentialsProviderConfiguration credentialsProviderConfiguration,
      final ObjectProvider<JsonMapper> jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ObjectProvider<CamundaClientExecutorService> executorService,
      final ObjectProvider<JobExceptionHandlerSupplier> jobExceptionHandlerSupplier) {
    this.credentialsProviderConfiguration = credentialsProviderConfiguration;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.executorService = executorService;
    this.jobExceptionHandlerSupplier = jobExceptionHandlerSupplier;
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
    // are configured, each gets its own owned executor sized by its execution-threads, so one
    // client's shutdown cannot stop another's and per-client sizing is honored.
    final CamundaClientExecutorService executor =
        useSharedExecutor
            ? executorService.getIfAvailable(
                () -> CamundaClientExecutorService.createDefault(properties.getExecutionThreads()))
            : CamundaClientExecutorService.createDefault(properties.getExecutionThreads());
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
}
