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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import io.camunda.client.CamundaClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

public class CamundaProcessTestClientProperties {
  public static final String PROPERTY_NAME_REST_ADDRESS = "client.restAddress";
  public static final String PROPERTY_NAME_GRPC_ADDRESS = "client.grpcAddress";
  public static final String PROPERTY_NAME_REQUEST_TIMEOUT = "client.requestTimeout";
  public static final String PROPERTY_NAME_REQUEST_TIMEOUT_OFFSET = "client.requestTimeoutOffset";
  public static final String PROPERTY_NAME_TENANT_ID = "client.tenantId";
  public static final String PROPERTY_NAME_MESSAGE_TIME_TO_LIVE = "client.messageTimeToLive";
  public static final String PROPERTY_NAME_MAX_MESSAGE_SIZE = "client.maxMessageSize";
  public static final String PROPERTY_NAME_MAX_METADATA_SIZE = "client.maxMetadataSize";
  public static final String PROPERTY_NAME_EXECUTION_THREADS = "client.executionThreads";
  public static final String PROPERTY_NAME_CA_CERTIFICATE_PATH = "client.caCertificatePath";
  public static final String PROPERTY_NAME_KEEP_ALIVE = "client.keepAlive";
  public static final String PROPERTY_NAME_OVERRIDE_AUTHORITY = "client.overrideAuthority";
  public static final String PROPERTY_NAME_PREFER_REST_OVER_GRPC = "client.preferRestOverGrpc";

  private final CamundaClientWorkerProperties clientWorkerProps;

  private final URI restAddress;
  private final URI grpcAddress;
  private final Duration requestTimeout;
  private final Duration requestTimeoutOffset;
  private final String tenantId;
  private final Duration messageTimeToLive;
  private final Integer maxMessageSize;
  private final Integer maxMetadataSize;
  private final Integer executionThreads;
  private final String caCertificatePath;
  private final Duration keepAlive;
  private final String overrideAuthority;
  private final Boolean preferRestOverGrpc;

  public CamundaProcessTestClientProperties(final Properties properties) {
    clientWorkerProps = new CamundaClientWorkerProperties(properties);

    restAddress = getPropertyOrNull(properties, PROPERTY_NAME_REST_ADDRESS, URI::create);
    grpcAddress = getPropertyOrNull(properties, PROPERTY_NAME_GRPC_ADDRESS, URI::create);
    requestTimeout = getPropertyOrNull(properties, PROPERTY_NAME_REQUEST_TIMEOUT, Duration::parse);
    requestTimeoutOffset =
        getPropertyOrNull(properties, PROPERTY_NAME_REQUEST_TIMEOUT_OFFSET, Duration::parse);
    messageTimeToLive =
        getPropertyOrNull(properties, PROPERTY_NAME_MESSAGE_TIME_TO_LIVE, Duration::parse);
    tenantId = getPropertyOrNull(properties, PROPERTY_NAME_TENANT_ID);
    maxMessageSize =
        getPropertyOrNull(properties, PROPERTY_NAME_MAX_MESSAGE_SIZE, Integer::parseInt);
    maxMetadataSize =
        getPropertyOrNull(properties, PROPERTY_NAME_MAX_METADATA_SIZE, Integer::parseInt);
    executionThreads =
        getPropertyOrNull(properties, PROPERTY_NAME_EXECUTION_THREADS, Integer::parseInt);
    caCertificatePath = getPropertyOrNull(properties, PROPERTY_NAME_CA_CERTIFICATE_PATH);
    keepAlive = getPropertyOrNull(properties, PROPERTY_NAME_KEEP_ALIVE, Duration::parse);
    overrideAuthority = getPropertyOrNull(properties, PROPERTY_NAME_OVERRIDE_AUTHORITY);
    preferRestOverGrpc =
        getPropertyOrDefault(
            properties, PROPERTY_NAME_PREFER_REST_OVER_GRPC, Boolean::parseBoolean, true);
  }

  public URI getRestAddress() {
    return restAddress;
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public Duration getRequestTimeoutOffset() {
    return requestTimeoutOffset;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Duration getMessageTimeToLive() {
    return messageTimeToLive;
  }

  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  public int getMaxMetadataSize() {
    return maxMetadataSize;
  }

  public int getExecutionThreads() {
    return executionThreads;
  }

  public String getCaCertificatePath() {
    return caCertificatePath;
  }

  public Duration getKeepAlive() {
    return keepAlive;
  }

  public String getOverrideAuthority() {
    return overrideAuthority;
  }

  public Boolean getPreferRestOverGrpc() {
    return preferRestOverGrpc;
  }

  public CamundaClientWorkerProperties getClientWorkerProps() {
    return clientWorkerProps;
  }

  public CamundaClientBuilder configureClientBuilder(final CamundaClientBuilder clientBuilder) {
    setIfExists(restAddress, clientBuilder::restAddress);
    setIfExists(grpcAddress, clientBuilder::grpcAddress);
    setIfExists(requestTimeout, clientBuilder::defaultRequestTimeout);
    setIfExists(requestTimeoutOffset, clientBuilder::defaultRequestTimeoutOffset);
    setIfExists(tenantId, clientBuilder::defaultTenantId);
    setIfExists(messageTimeToLive, clientBuilder::defaultMessageTimeToLive);
    setIfExists(maxMessageSize, clientBuilder::maxMessageSize);
    setIfExists(maxMetadataSize, clientBuilder::maxMetadataSize);
    setIfExists(executionThreads, clientBuilder::numJobWorkerExecutionThreads);
    setIfExists(caCertificatePath, clientBuilder::caCertificatePath);
    setIfExists(keepAlive, clientBuilder::keepAlive);
    setIfExists(overrideAuthority, clientBuilder::overrideAuthority);
    setIfExists(preferRestOverGrpc, clientBuilder::preferRestOverGrpc);

    setIfExists(clientWorkerProps.getPollInterval(), clientBuilder::defaultJobPollInterval);
    setIfExists(clientWorkerProps.getTimeout(), clientBuilder::defaultJobTimeout);
    setIfExists(clientWorkerProps.getMaxJobsActive(), clientBuilder::defaultJobWorkerMaxJobsActive);
    setIfExists(clientWorkerProps.getName(), clientBuilder::defaultJobWorkerName);
    setIfExists(clientWorkerProps.getTenantIds(), clientBuilder::defaultJobWorkerTenantIds);
    setIfExists(clientWorkerProps.getStreamEnabled(), clientBuilder::defaultJobWorkerStreamEnabled);

    return clientBuilder;
  }

  private static <T> void setIfExists(final T property, final Consumer<T> setter) {
    setIfExists(property, setter, Function.identity());
  }

  private static <T, U> void setIfExists(
      final T property, final Consumer<U> setter, final Function<T, U> transformer) {

    if (property != null) {
      setter.accept(transformer.apply(property));
    }
  }
}
