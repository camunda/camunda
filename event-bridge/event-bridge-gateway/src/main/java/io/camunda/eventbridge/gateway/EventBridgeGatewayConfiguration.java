/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway;

import io.camunda.eventbridge.broker.BrokerModuleConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring module configuration for the Event Bridge gateway. Activates component scanning for all
 * gateway beans and imports the broker module so that actor beans are available for injection into
 * the REST controller.
 *
 * <p>This class is intentionally a plain {@link Configuration} rather than a
 * {@code @SpringBootConfiguration}: it is loaded as a <em>source</em> by the standalone entry-point
 * ({@code StandaloneEventBridge}) which carries the {@code @SpringBootConfiguration} annotation and
 * handles {@code @EnableAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@Import(BrokerModuleConfiguration.class)
@ComponentScan(basePackages = "io.camunda.eventbridge.gateway")
public class EventBridgeGatewayConfiguration {}
