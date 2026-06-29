/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring module configuration for the Event Bridge gateway. Activates component scanning for the
 * gateway (REST controllers) and the {@code event-bridge-services} domain layer.
 *
 * <p>The gateway has no compile-time dependency on the broker module. In the single-JVM standalone
 * deployment the broker runs in the same process; its {@code EventBridgeBrokerConfiguration} is
 * composed by the standalone entry-point ({@code StandaloneEventBridge}) and is also picked up by
 * the broad component scan below whenever the broker is present on the classpath.
 *
 * <p>This class is intentionally a plain {@link Configuration} rather than a
 * {@code @SpringBootConfiguration}: it is loaded as a <em>source</em> by the standalone entry-point
 * which carries the {@code @SpringBootConfiguration} annotation and handles
 * {@code @EnableAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.eventbridge")
public class EventBridgeGatewayConfiguration {}
