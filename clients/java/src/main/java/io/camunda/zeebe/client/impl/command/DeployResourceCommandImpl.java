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
package io.camunda.zeebe.client.impl.command;

import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;

import com.google.protobuf.ByteString;
import io.camunda.client.protocol.rest.DeploymentResult;
import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.CommandWithTenantStep;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.DeploymentEventImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Resource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;

public final class DeployResourceCommandImpl
    implements DeployResourceCommandStep1, DeployResourceCommandStep2 {

  private static final String RESOURCES_FIELD_NAME = "resources";
  private static final String TENANT_FIELD_NAME = "tenantId";

  private final MultipartEntityBuilder multipartEntityBuilder =
      MultipartEntityBuilder.create().setContentType(ContentType.MULTIPART_FORM_DATA);
  private final DeployResourceRequest.Builder requestBuilder = DeployResourceRequest.newBuilder();
  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private boolean useRest;
  private final JsonMapper jsonMapper;
  private String tenantId;

  public DeployResourceCommandImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration config,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc,
      final JsonMapper jsonMapper) {
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    tenantId(config.getDefaultTenantId());
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    useRest = preferRestOverGrpc;
    this.jsonMapper = jsonMapper;
    requestTimeout(requestTimeout);
  }

  /**
   * A constructor that provides an instance with the <code><default></code> tenantId set.
   *
   * <p>From version 8.3.0, the java client supports multi-tenancy for this command, which requires
   * the <code>tenantId</code> property to be defined. This constructor is only intended for
   * backwards compatibility in tests.
   *
   * @deprecated since 8.3.0, use {@link
   *     DeployResourceCommandImpl#DeployResourceCommandImpl(GatewayStub asyncStub,
   *     ZeebeClientConfiguration config, Predicate retryPredicate)}
   */
  @Deprecated
  public DeployResourceCommandImpl(
      final GatewayStub asyncStub,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc,
      final JsonMapper jsonMapper) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    useRest = preferRestOverGrpc;
    this.jsonMapper = jsonMapper;
    tenantId(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
    requestTimeout(requestTimeout);
  }

  @Override
  public DeployResourceCommandStep2 addResourceBytes(
      final byte[] resource, final String resourceName) {

    if (useRest) {
      multipartEntityBuilder.addBinaryBody(
          RESOURCES_FIELD_NAME, resource, ContentType.APPLICATION_OCTET_STREAM, resourceName);
      return this;
    }

    requestBuilder.addResources(
        Resource.newBuilder()
            .setName(resourceName)
            .setContent(ByteString.copyFrom(resource))
            .build());
    return this;
  }

  @Override
  public DeployResourceCommandStep2 addResourceString(
      final String resource, final Charset charset, final String resourceName) {
    return addResourceBytes(resource.getBytes(charset), resourceName);
  }

  @Override
  public DeployResourceCommandStep2 addResourceStringUtf8(
      final String resourceString, final String resourceName) {
    return addResourceString(resourceString, StandardCharsets.UTF_8, resourceName);
  }

  @Override
  public DeployResourceCommandStep2 addResourceStream(
      final InputStream resourceStream, final String resourceName) {
    ensureNotNull("resource stream", resourceStream);

    try {
      final byte[] bytes = StreamUtil.readInputStream(resourceStream);

      return addResourceBytes(bytes, resourceName);
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy bpmn resource from stream. %s", e.getMessage());
      throw new ClientException(exceptionMsg, e);
    }
  }

  @Override
  public DeployResourceCommandStep2 addResourceFromClasspath(final String classpathResource) {
    ensureNotNull("classpath resource", classpathResource);

    try (final InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream(classpathResource)) {
      if (resourceStream != null) {
        return addResourceStream(resourceStream, classpathResource);
      } else {
        throw new FileNotFoundException(classpathResource);
      }

    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy resource from classpath. %s", e.getMessage());
      throw new RuntimeException(exceptionMsg, e);
    }
  }

  @Override
  public DeployResourceCommandStep2 addResourceFile(final String filename) {
    ensureNotNull("filename", filename);

    try (final InputStream resourceStream = new FileInputStream(filename)) {
      return addResourceStream(resourceStream, filename);
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy resource from file. %s", e.getMessage());
      throw new RuntimeException(exceptionMsg, e);
    }
  }

  @Override
  public DeployResourceCommandStep2 addProcessModel(
      final BpmnModelInstance processDefinition, final String resourceName) {
    ensureNotNull("process model", processDefinition);

    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, processDefinition);
    return addResourceBytes(outStream.toByteArray(), resourceName);
  }

  @Override
  public FinalCommandStep<DeploymentEvent> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<DeploymentEvent> send() {
    if (useRest) {
      // adding here the tenantId because in the multipart request fields are only appended
      multipartEntityBuilder.addTextBody(TENANT_FIELD_NAME, tenantId);
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  @Override
  public DeployResourceCommandStep2 tenantId(final String tenantId) {
    this.tenantId = tenantId;
    requestBuilder.setTenantId(tenantId);
    return this;
  }

  @Override
  public DeployResourceCommandStep2 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public DeployResourceCommandStep2 useGrpc() {
    useRest = false;
    return this;
  }

  private ZeebeFuture<DeploymentEvent> sendRestRequest() {
    final HttpZeebeFuture<DeploymentEvent> result = new HttpZeebeFuture<>();
    httpClient.postMultipart(
        "/deployments",
        multipartEntityBuilder,
        httpRequestConfig.build(),
        DeploymentResult.class,
        DeploymentEventImpl::new,
        result);
    return result;
  }

  private ZeebeFuture<DeploymentEvent> sendGrpcRequest() {
    final DeployResourceRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<DeploymentEvent, DeployResourceResponse> future =
        new RetriableClientFutureImpl<>(
            DeploymentEventImpl::new,
            retryPredicate,
            streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);

    return future;
  }

  private void sendGrpcRequest(
      final DeployResourceRequest request, final StreamObserver streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .deployResource(request, streamObserver);
  }
}
