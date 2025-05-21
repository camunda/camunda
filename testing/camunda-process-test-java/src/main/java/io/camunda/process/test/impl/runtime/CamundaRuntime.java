/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import io.camunda.client.CamundaClientBuilder;
import java.net.URI;
import java.util.function.Supplier;

public interface CamundaRuntime extends AutoCloseable {

  void start();

  URI getCamundaRestApiAddress();

  URI getCamundaGrpcApiAddress();

  URI getCamundaMonitoringApiAddress();

  URI getConnectorsRestApiAddress();

  Supplier<CamundaClientBuilder> getClientBuilderSupplier();
}
