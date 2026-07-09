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
 * <p>Collaborators that exist as beans in the multi-client context are reused: the per-client
 * {@link CredentialsProvider} is derived from the entry's {@code auth.*} via {@link
 * CredentialsProviderConfiguration}, and any user-defined {@link JsonMapper}, {@link
 * ClientInterceptor}s and {@link AsyncExecChainHandler}s are applied. The single-client executor
 * and exception-handler beans are not available in multi-client mode, so each client gets its own
 * executor (owned -&gt; closed with the client) and the default exception handling; unifying those
 * is tracked in the single-client/multi-client consolidation.
 */
public class CamundaClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientFactory.class);

  private final CredentialsProviderConfiguration credentialsProviderConfiguration;
  private final ObjectProvider<JsonMapper> jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;

  public CamundaClientFactory(
      final CredentialsProviderConfiguration credentialsProviderConfiguration,
      final ObjectProvider<JsonMapper> jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers) {
    this.credentialsProviderConfiguration = credentialsProviderConfiguration;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
  }

  public CamundaClient createClient(final String name, final CamundaClientProperties properties) {
    LOG.debug("Creating CamundaClient '{}'", name);
    final CredentialsProvider credentialsProvider =
        credentialsProviderConfiguration.camundaClientCredentialsProvider(properties);

    // Reuse the single-client adapter for the full property mapping, wired with the context
    // collaborators. The json mapper falls back to the client default when no bean is defined; the
    // executor and exception handler are self-contained (their single-client beans do not exist in
    // multi-client mode) — each client gets its own owned executor.
    final CamundaClientConfiguration configuration =
        new SpringCamundaClientConfiguration(
            properties,
            jsonMapper.getIfAvailable(),
            interceptors,
            chainHandlers,
            CamundaClientExecutorService.createDefault(properties.getExecutionThreads()),
            credentialsProvider,
            context -> JobExceptionHandler.createDefault());

    return CamundaClient.newClientBuilder()
        .withConfiguration(configuration)
        // the resolved Spring properties are authoritative; don't let client-level environment
        // variables override them (matching the single-client path)
        .applyEnvironmentVariableOverrides(false)
        .build();
  }
}
