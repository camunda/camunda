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

public class RemoteRuntimeProperties {
  public static final String PROPERTY_NAME_REMOTE_CAMUNDA_MONITORING_API_ADDRESS =
      "remote.camundaMonitoringApiAddress";
  public static final String PROPERTY_NAME_REMOTE_CONNECTORS_REST_API_ADDRESS =
      "remote.connectorsRestApiAddress";

  private final URI camundaMonitoringApiAddress;
  private final URI connectorsRestApiAddress;

  private final RemoteRuntimeClientProperties remoteClientProperties;

  public RemoteRuntimeProperties(final Properties properties) {
    camundaMonitoringApiAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CAMUNDA_MONITORING_API_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS;
              }
            },
            CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS);

    connectorsRestApiAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CONNECTORS_REST_API_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS;
              }
            },
            CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS);

    remoteClientProperties = new RemoteRuntimeClientProperties(properties);
  }

  public URI getCamundaMonitoringApiAddress() {
    return camundaMonitoringApiAddress;
  }

  public URI getConnectorsRestApiAddress() {
    return connectorsRestApiAddress;
  }

  public RemoteRuntimeClientProperties getRemoteClientProperties() {
    return remoteClientProperties;
  }
}
