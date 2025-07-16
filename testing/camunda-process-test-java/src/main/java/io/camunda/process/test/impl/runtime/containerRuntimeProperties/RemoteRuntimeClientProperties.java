/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime.containerRuntimeProperties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.net.URI;
import java.util.Properties;

public class RemoteRuntimeClientProperties {
  public static final String PROPERTY_NAME_REMOTE_CLIENT_GRPC_ADDRESS = "remote.client.grpcAddress";
  public static final String PROPERTY_NAME_REMOTE_CLIENT_REST_ADDRESS = "remote.client.restAddress";

  private final URI grpcAddress;
  private final URI restAddress;

  public RemoteRuntimeClientProperties(final Properties properties) {
    grpcAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CLIENT_GRPC_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS;
              }
            },
            null);

    restAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CLIENT_REST_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS;
              }
            },
            null);
  }

  public URI getGrpcAddress() {
    return grpcAddress;
  }

  public URI getRestAddress() {
    return restAddress;
  }
}
