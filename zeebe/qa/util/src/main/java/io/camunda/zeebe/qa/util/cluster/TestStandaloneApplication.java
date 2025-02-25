/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.function.Consumer;

/**
 * Application that allows to run standalone, with exporters, broker, gateway, etc.
 *
 * <p>Allows to configure and use exporters, the gateway, and broker.
 */
public interface TestStandaloneApplication<T extends TestStandaloneApplication<T>>
    extends TestApplication<T>, TestGateway<T> {

  /**
   * Registers or replaces a new exporter with the given ID. If it was already registered, the
   * existing configuration is passed to the modifier.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  T withExporter(final String id, final Consumer<ExporterCfg> modifier);

  /**
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  T withBrokerConfig(final Consumer<BrokerBasedProperties> modifier);

  BrokerBasedProperties brokerConfig();
}
