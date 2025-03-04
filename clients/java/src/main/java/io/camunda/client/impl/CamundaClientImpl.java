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
package io.camunda.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.AssignGroupToTenantCommandStep1;
import io.camunda.client.api.command.AssignMappingToTenantCommandStep1;
import io.camunda.client.api.command.AssignUserTaskCommandStep1;
import io.camunda.client.api.command.AssignUserToGroupCommandStep1;
import io.camunda.client.api.command.AssignUserToTenantCommandStep1;
import io.camunda.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClockPinCommandStep1;
import io.camunda.client.api.command.ClockResetCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentCommandStep1;
import io.camunda.client.api.command.CreateDocumentLinkCommandStep1;
import io.camunda.client.api.command.CreateGroupCommandStep1;
import io.camunda.client.api.command.CreateMappingCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.CreateRoleCommandStep1;
import io.camunda.client.api.command.CreateTenantCommandStep1;
import io.camunda.client.api.command.CreateUserCommandStep1;
import io.camunda.client.api.command.DeleteAuthorizationCommandStep1;
import io.camunda.client.api.command.DeleteDocumentCommandStep1;
import io.camunda.client.api.command.DeleteGroupCommandStep1;
import io.camunda.client.api.command.DeleteResourceCommandStep1;
import io.camunda.client.api.command.DeleteTenantCommandStep1;
import io.camunda.client.api.command.DeployProcessCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.RemoveUserFromTenantCommandStep1;
import io.camunda.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.client.api.command.SetVariablesCommandStep1;
import io.camunda.client.api.command.StreamJobsCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.command.UnassignGroupFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignUserFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignUserTaskCommandStep1;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1;
import io.camunda.client.api.command.UpdateGroupCommandStep1;
import io.camunda.client.api.command.UpdateJobCommandStep1;
import io.camunda.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.client.api.command.UpdateTenantCommandStep1;
import io.camunda.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.client.api.command.UpdateUserTaskCommandStep1;
import io.camunda.client.api.fetch.DecisionDefinitionGetRequest;
import io.camunda.client.api.fetch.DecisionDefinitionGetXmlRequest;
import io.camunda.client.api.fetch.DecisionInstanceGetRequest;
import io.camunda.client.api.fetch.DecisionRequirementsGetRequest;
import io.camunda.client.api.fetch.DecisionRequirementsGetXmlRequest;
import io.camunda.client.api.fetch.DocumentContentGetRequest;
import io.camunda.client.api.fetch.FlowNodeInstanceGetRequest;
import io.camunda.client.api.fetch.IncidentGetRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetFormRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.client.api.fetch.ProcessInstanceGetRequest;
import io.camunda.client.api.fetch.UserTaskGetFormRequest;
import io.camunda.client.api.fetch.UserTaskGetRequest;
import io.camunda.client.api.fetch.VariableGetRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.search.query.AdHocSubprocessActivityQuery;
import io.camunda.client.api.search.query.DecisionDefinitionQuery;
import io.camunda.client.api.search.query.DecisionInstanceQuery;
import io.camunda.client.api.search.query.DecisionRequirementsQuery;
import io.camunda.client.api.search.query.FlownodeInstanceQuery;
import io.camunda.client.api.search.query.IncidentQuery;
import io.camunda.client.api.search.query.ProcessDefinitionQuery;
import io.camunda.client.api.search.query.ProcessInstanceQuery;
import io.camunda.client.api.search.query.UserTaskQuery;
import io.camunda.client.api.search.query.UserTaskVariableQuery;
import io.camunda.client.api.search.query.VariableQuery;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.impl.command.AssignGroupToTenantCommandImpl;
import io.camunda.client.impl.command.AssignMappingToTenantCommandImpl;
import io.camunda.client.impl.command.AssignUserTaskCommandImpl;
import io.camunda.client.impl.command.AssignUserToGroupCommandImpl;
import io.camunda.client.impl.command.AssignUserToTenantCommandImpl;
import io.camunda.client.impl.command.BroadcastSignalCommandImpl;
import io.camunda.client.impl.command.CancelProcessInstanceCommandImpl;
import io.camunda.client.impl.command.ClockPinCommandImpl;
import io.camunda.client.impl.command.ClockResetCommandImpl;
import io.camunda.client.impl.command.CompleteUserTaskCommandImpl;
import io.camunda.client.impl.command.CorrelateMessageCommandImpl;
import io.camunda.client.impl.command.CreateAuthorizationCommandImpl;
import io.camunda.client.impl.command.CreateDocumentBatchCommandImpl;
import io.camunda.client.impl.command.CreateDocumentCommandImpl;
import io.camunda.client.impl.command.CreateDocumentLinkCommandImpl;
import io.camunda.client.impl.command.CreateGroupCommandImpl;
import io.camunda.client.impl.command.CreateMappingCommandImpl;
import io.camunda.client.impl.command.CreateProcessInstanceCommandImpl;
import io.camunda.client.impl.command.CreateRoleCommandImpl;
import io.camunda.client.impl.command.CreateTenantCommandImpl;
import io.camunda.client.impl.command.CreateUserCommandImpl;
import io.camunda.client.impl.command.DeleteAuthorizationCommandImpl;
import io.camunda.client.impl.command.DeleteDocumentCommandImpl;
import io.camunda.client.impl.command.DeleteGroupCommandImpl;
import io.camunda.client.impl.command.DeleteResourceCommandImpl;
import io.camunda.client.impl.command.DeleteTenantCommandImpl;
import io.camunda.client.impl.command.DeployProcessCommandImpl;
import io.camunda.client.impl.command.DeployResourceCommandImpl;
import io.camunda.client.impl.command.EvaluateDecisionCommandImpl;
import io.camunda.client.impl.command.JobUpdateCommandImpl;
import io.camunda.client.impl.command.JobUpdateRetriesCommandImpl;
import io.camunda.client.impl.command.JobUpdateTimeoutCommandImpl;
import io.camunda.client.impl.command.MigrateProcessInstanceCommandImpl;
import io.camunda.client.impl.command.ModifyProcessInstanceCommandImpl;
import io.camunda.client.impl.command.PublishMessageCommandImpl;
import io.camunda.client.impl.command.RemoveUserFromTenantCommandImpl;
import io.camunda.client.impl.command.ResolveIncidentCommandImpl;
import io.camunda.client.impl.command.SetVariablesCommandImpl;
import io.camunda.client.impl.command.StreamJobsCommandImpl;
import io.camunda.client.impl.command.TopologyRequestImpl;
import io.camunda.client.impl.command.UnassignGroupFromTenantCommandImpl;
import io.camunda.client.impl.command.UnassignUserFromGroupCommandImpl;
import io.camunda.client.impl.command.UnassignUserTaskCommandImpl;
import io.camunda.client.impl.command.UpdateAuthorizationCommandImpl;
import io.camunda.client.impl.command.UpdateGroupCommandImpl;
import io.camunda.client.impl.command.UpdateTenantCommandImpl;
import io.camunda.client.impl.command.UpdateUserTaskCommandImpl;
import io.camunda.client.impl.fetch.DecisionDefinitionGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionDefinitionGetXmlRequestImpl;
import io.camunda.client.impl.fetch.DecisionInstanceGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionRequirementsGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionRequirementsGetXmlRequestImpl;
import io.camunda.client.impl.fetch.DocumentContentGetRequestImpl;
import io.camunda.client.impl.fetch.FlowNodeInstanceGetRequestImpl;
import io.camunda.client.impl.fetch.IncidentGetRequestImpl;
import io.camunda.client.impl.fetch.ProcessDefinitionGetFormRequestImpl;
import io.camunda.client.impl.fetch.ProcessDefinitionGetRequestImpl;
import io.camunda.client.impl.fetch.ProcessDefinitionGetXmlRequestImpl;
import io.camunda.client.impl.fetch.ProcessInstanceGetRequestImpl;
import io.camunda.client.impl.fetch.UserTaskGetFormRequestImpl;
import io.camunda.client.impl.fetch.UserTaskGetRequestImpl;
import io.camunda.client.impl.fetch.VariableGetRequestImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.HttpClientFactory;
import io.camunda.client.impl.search.query.AdHocSubprocessActivityQueryImpl;
import io.camunda.client.impl.search.query.DecisionDefinitionQueryImpl;
import io.camunda.client.impl.search.query.DecisionInstanceQueryImpl;
import io.camunda.client.impl.search.query.DecisionRequirementsQueryImpl;
import io.camunda.client.impl.search.query.FlowNodeInstanceQueryImpl;
import io.camunda.client.impl.search.query.IncidentQueryImpl;
import io.camunda.client.impl.search.query.ProcessDefinitionQueryImpl;
import io.camunda.client.impl.search.query.ProcessInstanceQueryImpl;
import io.camunda.client.impl.search.query.UserTaskQueryImpl;
import io.camunda.client.impl.search.query.UserTaskVariableQueryImpl;
import io.camunda.client.impl.search.query.VariableQueryImpl;
import io.camunda.client.impl.util.ExecutorResource;
import io.camunda.client.impl.util.VersionUtil;
import io.camunda.client.impl.worker.JobClientImpl;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CamundaClientImpl implements CamundaClient {
  private final CamundaClientConfiguration config;
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final ManagedChannel channel;
  private final ExecutorResource executorResource;
  private final List<Closeable> closeables = new CopyOnWriteArrayList<>();
  private final JobClient jobClient;
  private final CredentialsProvider credentialsProvider;
  private final HttpClient httpClient;

  public CamundaClientImpl(final CamundaClientConfiguration configuration) {
    this(configuration, buildChannel(configuration));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration configuration, final ManagedChannel channel) {
    this(configuration, channel, buildGatewayStub(channel, configuration));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration configuration,
      final ManagedChannel channel,
      final HttpClient httpClient) {
    this(
        configuration,
        channel,
        buildGatewayStub(channel, configuration),
        buildExecutorService(configuration),
        httpClient);
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration configuration,
      final ManagedChannel channel,
      final GatewayStub gatewayStub) {
    this(configuration, channel, gatewayStub, buildExecutorService(configuration));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final ExecutorResource executorResource) {
    this(config, channel, gatewayStub, executorResource, buildHttpClient(config));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final ExecutorResource executorResource,
      final HttpClient httpClient) {
    this.config = config;
    jsonMapper = config.getJsonMapper();
    this.channel = channel;
    asyncStub = gatewayStub;
    this.executorResource = executorResource;
    this.httpClient = httpClient;

    if (config.getCredentialsProvider() != null) {
      credentialsProvider = config.getCredentialsProvider();
    } else {
      credentialsProvider = new NoopCredentialsProvider();
    }
    jobClient = newJobClient();
    this.httpClient.start();
  }

  private static HttpClient buildHttpClient(final CamundaClientConfiguration config) {
    return new HttpClientFactory(config).createClient();
  }

  public static ManagedChannel buildChannel(final CamundaClientConfiguration config) {
    final URI address;
    address = config.getGrpcAddress();

    final NettyChannelBuilder channelBuilder =
        NettyChannelBuilder.forAddress(address.getHost(), address.getPort());

    configureConnectionSecurity(config, channelBuilder);
    channelBuilder.keepAliveTime(config.getKeepAlive().toMillis(), TimeUnit.MILLISECONDS);
    channelBuilder.userAgent("camunda-client-java/" + VersionUtil.getVersion());
    channelBuilder.maxInboundMessageSize(config.getMaxMessageSize());
    channelBuilder.maxInboundMetadataSize(config.getMaxMetadataSize());

    if (config.useDefaultRetryPolicy()) {
      final Map<String, Object> serviceConfig = defaultServiceConfig();
      if (!serviceConfig.isEmpty()) {
        channelBuilder.defaultServiceConfig(serviceConfig);
        channelBuilder.enableRetry();
      }
    }

    return channelBuilder.build();
  }

  private static CallCredentials buildCallCredentials(final CamundaClientConfiguration config) {
    final CredentialsProvider customCredentialsProvider = config.getCredentialsProvider();

    if (customCredentialsProvider == null) {
      return null;
    }

    return new CamundaCallCredentials(customCredentialsProvider);
  }

  private static void configureConnectionSecurity(
      final CamundaClientConfiguration config, final NettyChannelBuilder channelBuilder) {
    if (!config.isPlaintextConnectionEnabled()) {
      final String certificatePath = config.getCaCertificatePath();
      SslContext sslContext = null;

      if (certificatePath != null) {
        if (certificatePath.isEmpty()) {
          throw new IllegalArgumentException(
              "Expected valid certificate path but found empty path instead.");
        }

        try (final FileInputStream certInputStream = new FileInputStream(certificatePath)) {
          sslContext = GrpcSslContexts.forClient().trustManager(certInputStream).build();
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }

      channelBuilder.useTransportSecurity().sslContext(sslContext);
      if (config.getOverrideAuthority() != null) {
        channelBuilder.overrideAuthority(config.getOverrideAuthority());
      }
    } else {
      channelBuilder.usePlaintext();
    }
  }

  public static GatewayStub buildGatewayStub(
      final ManagedChannel channel, final CamundaClientConfiguration config) {
    final CallCredentials credentials = buildCallCredentials(config);
    final GatewayStub gatewayStub = GatewayGrpc.newStub(channel).withCallCredentials(credentials);
    if (!config.getInterceptors().isEmpty()) {
      return gatewayStub.withInterceptors(
          config.getInterceptors().toArray(new ClientInterceptor[] {}));
    }
    return gatewayStub;
  }

  private static Map<String, Object> defaultServiceConfig() {
    final ObjectMapper objectMapper = new ObjectMapper();
    final URL defaultServiceConfig =
        ClassLoader.getSystemClassLoader().getResource("gateway-service-config.json");
    if (defaultServiceConfig == null) {
      Loggers.LOGGER.info(
          "No default service config found on classpath; will not configure a default retry policy");
      return new HashMap<>();
    }

    try {
      return objectMapper.readValue(
          defaultServiceConfig, new TypeReference<Map<String, Object>>() {});
    } catch (final IOException e) {
      Loggers.LOGGER.warn(
          "Failed to read default service config from classpath; will not configure a default retry policy",
          e);
      return new HashMap<>();
    }
  }

  private static ExecutorResource buildExecutorService(
      final CamundaClientConfiguration configuration) {
    if (configuration.jobWorkerExecutor() != null) {
      return new ExecutorResource(
          configuration.jobWorkerExecutor(), configuration.ownsJobWorkerExecutor());
    }

    final int threadCount = configuration.getNumJobWorkerExecutionThreads();
    final ScheduledExecutorService executor = Executors.newScheduledThreadPool(threadCount);
    return new ExecutorResource(executor, true);
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(
        asyncStub,
        httpClient,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        config.preferRestOverGrpc());
  }

  @Override
  public CamundaClientConfiguration getConfiguration() {
    return config;
  }

  @Override
  public void close() {
    closeables.forEach(
        c -> {
          try {
            c.close();
          } catch (final IOException e) {
            // ignore
          }
        });

    executorResource.close();
    channel.shutdownNow();

    try {
      if (!channel.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException(
            "Timed out awaiting termination of in-flight request channel after 15 seconds");
      }
    } catch (final InterruptedException e) {
      throw new ClientException(
          "Unexpectedly interrupted awaiting termination of in-flight request channel", e);
    }

    try {
      httpClient.close();
    } catch (final Exception e) {
      throw new ClientException("Failed closing http client.", e);
    }
  }

  @Override
  public DeployProcessCommandStep1 newDeployCommand() {
    return new DeployProcessCommandImpl(
        asyncStub, config.getDefaultRequestTimeout(), credentialsProvider::shouldRetryRequest);
  }

  @Override
  public DeployResourceCommandStep1 newDeployResourceCommand() {
    return new DeployResourceCommandImpl(
        asyncStub,
        config,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public CreateProcessInstanceCommandStep1 newCreateInstanceCommand() {
    return new CreateProcessInstanceCommandImpl(
        asyncStub,
        jsonMapper,
        config,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public ModifyProcessInstanceCommandStep1 newModifyProcessInstanceCommand(
      final long processInstanceKey) {
    return new ModifyProcessInstanceCommandImpl(
        processInstanceKey,
        jsonMapper,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config);
  }

  @Override
  public MigrateProcessInstanceCommandStep1 newMigrateProcessInstanceCommand(
      final long processInstanceKey) {
    return new MigrateProcessInstanceCommandImpl(
        processInstanceKey,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config,
        jsonMapper);
  }

  @Override
  public CancelProcessInstanceCommandStep1 newCancelInstanceCommand(final long processInstanceKey) {
    return new CancelProcessInstanceCommandImpl(
        asyncStub,
        processInstanceKey,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config,
        jsonMapper);
  }

  @Override
  public SetVariablesCommandStep1 newSetVariablesCommand(final long elementInstanceKey) {
    return new SetVariablesCommandImpl(
        asyncStub,
        jsonMapper,
        elementInstanceKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public EvaluateDecisionCommandStep1 newEvaluateDecisionCommand() {
    return new EvaluateDecisionCommandImpl(
        asyncStub, jsonMapper, config, credentialsProvider::shouldRetryRequest, httpClient);
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    return new PublishMessageCommandImpl(
        asyncStub,
        config,
        jsonMapper,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public CorrelateMessageCommandStep1 newCorrelateMessageCommand() {
    return new CorrelateMessageCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public BroadcastSignalCommandStep1 newBroadcastSignalCommand() {
    return new BroadcastSignalCommandImpl(
        asyncStub, config, jsonMapper, credentialsProvider::shouldRetryRequest, httpClient);
  }

  @Override
  public ResolveIncidentCommandStep1 newResolveIncidentCommand(final long incidentKey) {
    return new ResolveIncidentCommandImpl(
        asyncStub,
        incidentKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final long jobKey) {
    return new JobUpdateRetriesCommandImpl(
        asyncStub,
        jobKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final ActivatedJob job) {
    return newUpdateRetriesCommand(job.getKey());
  }

  @Override
  public UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(final long jobKey) {
    return new JobUpdateTimeoutCommandImpl(
        asyncStub,
        jobKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(final ActivatedJob job) {
    return newUpdateTimeoutCommand(job.getKey());
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return new JobWorkerBuilderImpl(config, jobClient, executorResource.executor(), closeables);
  }

  @Override
  public DeleteResourceCommandStep1 newDeleteResourceCommand(final long resourceKey) {
    return new DeleteResourceCommandImpl(
        resourceKey,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config,
        jsonMapper);
  }

  @Override
  public CompleteUserTaskCommandStep1 newUserTaskCompleteCommand(final long userTaskKey) {
    return new CompleteUserTaskCommandImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public AssignUserTaskCommandStep1 newUserTaskAssignCommand(final long userTaskKey) {
    return new AssignUserTaskCommandImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public UpdateUserTaskCommandStep1 newUserTaskUpdateCommand(final long userTaskKey) {
    return new UpdateUserTaskCommandImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public UnassignUserTaskCommandStep1 newUserTaskUnassignCommand(final long userTaskKey) {
    return new UnassignUserTaskCommandImpl(httpClient, userTaskKey);
  }

  @Override
  public UpdateJobCommandStep1 newUpdateJobCommand(final long jobKey) {
    return new JobUpdateCommandImpl(jobKey, httpClient, jsonMapper);
  }

  @Override
  public UpdateJobCommandStep1 newUpdateJobCommand(final ActivatedJob job) {
    return newUpdateJobCommand(job.getKey());
  }

  @Override
  public ClockPinCommandStep1 newClockPinCommand() {
    return new ClockPinCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public ClockResetCommandStep1 newClockResetCommand() {
    return new ClockResetCommandImpl(httpClient);
  }

  @Override
  public ProcessDefinitionGetRequest newProcessDefinitionGetRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionGetRequestImpl(httpClient, processDefinitionKey);
  }

  @Override
  public ProcessDefinitionGetXmlRequest newProcessDefinitionGetXmlRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionGetXmlRequestImpl(httpClient, processDefinitionKey);
  }

  @Override
  public ProcessDefinitionGetFormRequest newProcessDefinitionGetFormRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionGetFormRequestImpl(httpClient, processDefinitionKey);
  }

  @Override
  public ProcessDefinitionQuery newProcessDefinitionQuery() {
    return new ProcessDefinitionQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public ProcessInstanceGetRequest newProcessInstanceGetRequest(final long processInstanceKey) {
    return new ProcessInstanceGetRequestImpl(httpClient, processInstanceKey);
  }

  @Override
  public ProcessInstanceQuery newProcessInstanceQuery() {
    return new ProcessInstanceQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public FlownodeInstanceQuery newFlownodeInstanceQuery() {
    return new FlowNodeInstanceQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public FlowNodeInstanceGetRequest newFlowNodeInstanceGetRequest(final long flowNodeInstanceKey) {
    return new FlowNodeInstanceGetRequestImpl(httpClient, flowNodeInstanceKey);
  }

  @Override
  public AdHocSubprocessActivityQuery newAdHocSubprocessActivityQuery() {
    return new AdHocSubprocessActivityQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public AdHocSubprocessActivityQuery newAdHocSubprocessActivityQuery(
      final long processDefinitionKey, final String adHocSubprocessId) {
    return newAdHocSubprocessActivityQuery()
        .filter(
            filter ->
                filter
                    .processDefinitionKey(processDefinitionKey)
                    .adHocSubprocessId(adHocSubprocessId));
  }

  @Override
  public UserTaskQuery newUserTaskQuery() {
    return new UserTaskQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionRequirementsQuery newDecisionRequirementsQuery() {
    return new DecisionRequirementsQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionDefinitionQuery newDecisionDefinitionQuery() {
    return new DecisionDefinitionQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionDefinitionGetRequest newDecisionDefinitionGetRequest(
      final long decisionDefinitionKey) {
    return new DecisionDefinitionGetRequestImpl(httpClient, decisionDefinitionKey);
  }

  @Override
  public DecisionDefinitionGetXmlRequest newDecisionDefinitionGetXmlRequest(
      final long decisionKey) {
    return new DecisionDefinitionGetXmlRequestImpl(httpClient, decisionKey);
  }

  @Override
  public DecisionInstanceQuery newDecisionInstanceQuery() {
    return new DecisionInstanceQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionInstanceGetRequest newDecisionInstanceGetRequest(final String decisionInstanceId) {
    return new DecisionInstanceGetRequestImpl(httpClient, jsonMapper, decisionInstanceId);
  }

  @Override
  public IncidentQuery newIncidentQuery() {
    return new IncidentQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public IncidentGetRequest newIncidentGetRequest(final long incidentKey) {
    return new IncidentGetRequestImpl(httpClient, incidentKey);
  }

  @Override
  public CreateRoleCommandStep1 newCreateRoleCommand() {
    return new CreateRoleCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public CreateGroupCommandStep1 newCreateGroupCommand() {
    return new CreateGroupCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public UpdateGroupCommandStep1 newUpdateGroupCommand(final long groupKey) {
    return new UpdateGroupCommandImpl(groupKey, httpClient, jsonMapper);
  }

  @Override
  public DeleteGroupCommandStep1 newDeleteGroupCommand(final long groupKey) {
    return new DeleteGroupCommandImpl(groupKey, httpClient);
  }

  @Override
  public AssignUserToGroupCommandStep1 newAssignUserToGroupCommand(final long groupKey) {
    return new AssignUserToGroupCommandImpl(groupKey, httpClient);
  }

  @Override
  public UnassignUserFromGroupCommandStep1 newUnassignUserFromGroupCommand(final long groupKey) {
    return new UnassignUserFromGroupCommandImpl(groupKey, httpClient);
  }

  @Override
  public CreateUserCommandStep1 newUserCreateCommand() {
    return new CreateUserCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public CreateMappingCommandStep1 newCreateMappingCommand() {
    return new CreateMappingCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionRequirementsGetXmlRequest newDecisionRequirementsGetXmlRequest(
      final long decisionRequirementsKey) {
    return new DecisionRequirementsGetXmlRequestImpl(httpClient, decisionRequirementsKey);
  }

  @Override
  public DecisionRequirementsGetRequest newDecisionRequirementsGetRequest(
      final long decisionRequirementsKey) {
    return new DecisionRequirementsGetRequestImpl(httpClient, decisionRequirementsKey);
  }

  @Override
  public UserTaskGetFormRequest newUserTaskGetFormRequest(final long userTaskKey) {
    return new UserTaskGetFormRequestImpl(httpClient, userTaskKey);
  }

  @Override
  public UserTaskGetRequest newUserTaskGetRequest(final long userTaskKey) {
    return new UserTaskGetRequestImpl(httpClient, userTaskKey);
  }

  @Override
  public VariableQuery newVariableQuery() {
    return new VariableQueryImpl(httpClient, jsonMapper);
  }

  @Override
  public VariableGetRequest newVariableGetRequest(final long variableKey) {
    return new VariableGetRequestImpl(httpClient, variableKey);
  }

  @Override
  public UserTaskVariableQuery newUserTaskVariableQuery(final long userTaskKey) {
    return new UserTaskVariableQueryImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public CreateDocumentCommandStep1 newCreateDocumentCommand() {
    return new CreateDocumentCommandImpl(jsonMapper, httpClient, config);
  }

  @Override
  public CreateDocumentBatchCommandStep1 newCreateDocumentBatchCommand() {
    return new CreateDocumentBatchCommandImpl(jsonMapper, httpClient, config);
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(final String documentId) {
    return new DocumentContentGetRequestImpl(httpClient, documentId, null, null, config);
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(
      final DocumentReferenceResponse documentReference) {
    return new DocumentContentGetRequestImpl(
        httpClient,
        documentReference.getDocumentId(),
        documentReference.getStoreId(),
        documentReference.getContentHash(),
        config);
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(final String documentId) {
    return new CreateDocumentLinkCommandImpl(
        documentId, null, null, jsonMapper, httpClient, config);
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(
      final DocumentReferenceResponse documentReference) {
    return new CreateDocumentLinkCommandImpl(
        documentReference.getDocumentId(),
        documentReference.getStoreId(),
        documentReference.getContentHash(),
        jsonMapper,
        httpClient,
        config);
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(final String documentId) {
    return new DeleteDocumentCommandImpl(documentId, null, httpClient, config);
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(
      final DocumentReferenceResponse documentReference) {
    return new DeleteDocumentCommandImpl(
        documentReference.getDocumentId(), documentReference.getStoreId(), httpClient, config);
  }

  @Override
  public CreateTenantCommandStep1 newCreateTenantCommand() {
    return new CreateTenantCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public UpdateTenantCommandStep1 newUpdateTenantCommand(final String tenantId) {
    return new UpdateTenantCommandImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public DeleteTenantCommandStep1 newDeleteTenantCommand(final String tenantId) {
    return new DeleteTenantCommandImpl(httpClient).tenantId(tenantId);
  }

  @Override
  public AssignMappingToTenantCommandStep1 newAssignMappingToTenantCommand(final String tenantId) {
    return new AssignMappingToTenantCommandImpl(httpClient, tenantId);
  }

  @Override
  public AssignUserToTenantCommandStep1 newAssignUserToTenantCommand(final String tenantId) {
    return new AssignUserToTenantCommandImpl(httpClient, tenantId);
  }

  @Override
  public RemoveUserFromTenantCommandStep1 newUnassignUserFromTenantCommand(final String tenantId) {
    return new RemoveUserFromTenantCommandImpl(httpClient, tenantId);
  }

  @Override
  public AssignGroupToTenantCommandStep1 newAssignGroupToTenantCommand(final String tenantId) {
    return new AssignGroupToTenantCommandImpl(httpClient, tenantId);
  }

  @Override
  public UnassignGroupFromTenantCommandStep1 newUnassignGroupFromTenantCommand(
      final String tenantId) {
    return new UnassignGroupFromTenantCommandImpl(httpClient, tenantId);
  }

  @Override
  public CreateAuthorizationCommandStep1 newCreateAuthorizationCommand() {
    return new CreateAuthorizationCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public DeleteAuthorizationCommandStep1 newDeleteAuthorizationCommand(
      final long authorizationKey) {
    return new DeleteAuthorizationCommandImpl(httpClient, authorizationKey);
  }

  @Override
  public UpdateAuthorizationCommandStep1 newUpdateAuthorizationCommand(
      final long authorizationKey) {
    return new UpdateAuthorizationCommandImpl(httpClient, jsonMapper, authorizationKey);
  }

  private JobClient newJobClient() {
    return new JobClientImpl(
        asyncStub, httpClient, config, jsonMapper, credentialsProvider::shouldRetryRequest);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final long jobKey) {
    return jobClient.newCompleteCommand(jobKey);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final ActivatedJob job) {
    return newCompleteCommand(job.getKey());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final long jobKey) {
    return jobClient.newFailCommand(jobKey);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final ActivatedJob job) {
    return newFailCommand(job.getKey());
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final long jobKey) {
    return jobClient.newThrowErrorCommand(jobKey);
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final ActivatedJob job) {
    return newThrowErrorCommand(job.getKey());
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    return jobClient.newActivateJobsCommand();
  }

  @Override
  public StreamJobsCommandStep1 newStreamJobsCommand() {
    return new StreamJobsCommandImpl(
        asyncStub, jsonMapper, credentialsProvider::shouldRetryRequest, config);
  }
}
