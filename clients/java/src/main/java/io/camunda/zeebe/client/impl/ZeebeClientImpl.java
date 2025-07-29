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
package io.camunda.zeebe.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.command.AssignUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.zeebe.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClockPinCommandStep1;
import io.camunda.zeebe.client.api.command.ClockResetCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.zeebe.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.zeebe.client.api.command.CreateDocumentCommandStep1;
import io.camunda.zeebe.client.api.command.CreateDocumentLinkCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeleteDocumentCommandStep1;
import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.api.command.UnassignUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateUserTaskCommandStep1;
import io.camunda.zeebe.client.api.fetch.DecisionDefinitionGetXmlRequest;
import io.camunda.zeebe.client.api.fetch.DocumentContentGetRequest;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DocumentReferenceResponse;
import io.camunda.zeebe.client.api.search.query.DecisionDefinitionQuery;
import io.camunda.zeebe.client.api.search.query.DecisionRequirementsQuery;
import io.camunda.zeebe.client.api.search.query.FlownodeInstanceQuery;
import io.camunda.zeebe.client.api.search.query.IncidentQuery;
import io.camunda.zeebe.client.api.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.client.api.search.query.UserTaskQuery;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.impl.command.AssignUserTaskCommandImpl;
import io.camunda.zeebe.client.impl.command.BroadcastSignalCommandImpl;
import io.camunda.zeebe.client.impl.command.CancelProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.ClockPinCommandImpl;
import io.camunda.zeebe.client.impl.command.ClockResetCommandImpl;
import io.camunda.zeebe.client.impl.command.CompleteUserTaskCommandImpl;
import io.camunda.zeebe.client.impl.command.CorrelateMessageCommandImpl;
import io.camunda.zeebe.client.impl.command.CreateDocumentBatchCommandImpl;
import io.camunda.zeebe.client.impl.command.CreateDocumentCommandImpl;
import io.camunda.zeebe.client.impl.command.CreateDocumentLinkCommandImpl;
import io.camunda.zeebe.client.impl.command.CreateProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.DeleteDocumentCommandImpl;
import io.camunda.zeebe.client.impl.command.DeleteResourceCommandImpl;
import io.camunda.zeebe.client.impl.command.DeployProcessCommandImpl;
import io.camunda.zeebe.client.impl.command.DeployResourceCommandImpl;
import io.camunda.zeebe.client.impl.command.EvaluateDecisionCommandImpl;
import io.camunda.zeebe.client.impl.command.JobUpdateCommandImpl;
import io.camunda.zeebe.client.impl.command.JobUpdateRetriesCommandImpl;
import io.camunda.zeebe.client.impl.command.JobUpdateTimeoutCommandImpl;
import io.camunda.zeebe.client.impl.command.MigrateProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.ModifyProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.PublishMessageCommandImpl;
import io.camunda.zeebe.client.impl.command.ResolveIncidentCommandImpl;
import io.camunda.zeebe.client.impl.command.SetVariablesCommandImpl;
import io.camunda.zeebe.client.impl.command.StreamJobsCommandImpl;
import io.camunda.zeebe.client.impl.command.TopologyRequestImpl;
import io.camunda.zeebe.client.impl.command.UnassignUserTaskCommandImpl;
import io.camunda.zeebe.client.impl.command.UpdateUserTaskCommandImpl;
import io.camunda.zeebe.client.impl.fetch.DocumentContentGetRequestImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpClientFactory;
import io.camunda.zeebe.client.impl.util.ExecutorResource;
import io.camunda.zeebe.client.impl.util.VersionUtil;
import io.camunda.zeebe.client.impl.worker.JobClientImpl;
import io.camunda.zeebe.client.impl.worker.JobWorkerBuilderImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZeebeClientImpl implements ZeebeClient {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeClientImpl.class);
  private static final String ZEEBE_DEPRECATION_WARNING =
      "{} is deprecated and will be removed in version 8.10. Please migrate to {}";
  private static final String UNSUPPORTED_OPERATION_MSG =
      String.format(
          "Not supported with %s. Please use %s.",
          ZeebeClient.class.getName(), CamundaClient.class.getName());
  private final ZeebeClientConfiguration config;
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final ManagedChannel channel;
  private final ExecutorResource executorResource;
  private final List<Closeable> closeables = new CopyOnWriteArrayList<>();
  private final JobClient jobClient;
  private final CredentialsProvider credentialsProvider;
  private final HttpClient httpClient;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this(configuration, buildChannel(configuration));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration, final ManagedChannel channel) {
    this(configuration, channel, buildGatewayStub(channel, configuration));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration,
      final ManagedChannel channel,
      final HttpClient httpClient) {
    this(
        configuration,
        channel,
        buildGatewayStub(channel, configuration),
        buildExecutorService(configuration),
        httpClient);
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration,
      final ManagedChannel channel,
      final GatewayStub gatewayStub) {
    this(configuration, channel, gatewayStub, buildExecutorService(configuration));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final ExecutorResource executorResource) {
    this(config, channel, gatewayStub, executorResource, buildHttpClient(config));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final ExecutorResource executorResource,
      final HttpClient httpClient) {
    LOG.warn(
        ZEEBE_DEPRECATION_WARNING,
        ZeebeClient.class.getSimpleName(),
        CamundaClient.class.getSimpleName());
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

  private static HttpClient buildHttpClient(final ZeebeClientConfiguration config) {
    return new HttpClientFactory(config).createClient();
  }

  public static ManagedChannel buildChannel(final ZeebeClientConfiguration config) {
    final URI address;
    address = config.getGrpcAddress();

    final NettyChannelBuilder channelBuilder =
        NettyChannelBuilder.forAddress(address.getHost(), address.getPort());

    configureConnectionSecurity(config, channelBuilder);
    channelBuilder.keepAliveTime(config.getKeepAlive().toMillis(), TimeUnit.MILLISECONDS);
    channelBuilder.userAgent("zeebe-client-java/" + VersionUtil.getVersion());
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

  private static CallCredentials buildCallCredentials(final ZeebeClientConfiguration config) {
    final CredentialsProvider customCredentialsProvider = config.getCredentialsProvider();

    if (customCredentialsProvider == null) {
      return null;
    }

    return new ZeebeCallCredentials(customCredentialsProvider);
  }

  private static void configureConnectionSecurity(
      final ZeebeClientConfiguration config, final NettyChannelBuilder channelBuilder) {
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
      final ManagedChannel channel, final ZeebeClientConfiguration config) {
    final CallCredentials credentials = buildCallCredentials(config);
    final GatewayStub gatewayStub = GatewayGrpc.newStub(channel).withCallCredentials(credentials);
    final List<ClientInterceptor> configInterceptors = config.getInterceptors();
    final List<ClientInterceptor> defaultInterceptors = getDefaultInterceptors();

    if (!configInterceptors.isEmpty()) {
      final List<ClientInterceptor> allInterceptors = new ArrayList<>(configInterceptors);
      allInterceptors.addAll(defaultInterceptors);
      return gatewayStub.withInterceptors(allInterceptors.toArray(new ClientInterceptor[] {}));
    }
    return gatewayStub.withInterceptors(
        defaultInterceptors.toArray(new ClientInterceptor[defaultInterceptors.size()]));
  }

  private static List<ClientInterceptor> getDefaultInterceptors() {
    final List<ClientInterceptor> defaultInterceptors = new ArrayList<>();
    defaultInterceptors.add(getZeebeGrpcDeprecationInterceptor());
    return defaultInterceptors;
  }

  private static ClientInterceptor getZeebeGrpcDeprecationInterceptor() {
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          final MethodDescriptor<ReqT, RespT> methodDescriptor,
          final CallOptions callOptions,
          final Channel channel) {
        LOG.warn(
            ZEEBE_DEPRECATION_WARNING,
            ZeebeClient.class.getSimpleName(),
            CamundaClient.class.getSimpleName());
        return channel.newCall(methodDescriptor, callOptions);
      }
    };
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
      final ZeebeClientConfiguration configuration) {
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
  public ZeebeClientConfiguration getConfiguration() {
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
        config.preferRestOverGrpc(),
        jsonMapper);
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
  public ProcessInstanceQuery newProcessInstanceQuery() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public FlownodeInstanceQuery newFlownodeInstanceQuery() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public UserTaskQuery newUserTaskQuery() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public DecisionRequirementsQuery newDecisionRequirementsQuery() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public DecisionDefinitionQuery newDecisionDefinitionQuery() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public DecisionDefinitionGetXmlRequest newDecisionDefinitionGetXmlRequest(
      final long decisionKey) {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
  }

  @Override
  public IncidentQuery newIncidentQuery() {
    throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MSG);
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
