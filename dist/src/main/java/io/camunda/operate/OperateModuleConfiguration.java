/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.gateway.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the Operate modules by using the the {@link
 * io.camunda.application.Profile#OPERATE} profile, so that the appropriate Operate application
 * properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.operate",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.webapp\\..*")
    },
    // use fully qualified names as bean name, as we have classes with same names for different
    // versions of importer
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile("operate")
@ConditionalOnSecondaryStorageEnabled
public class OperateModuleConfiguration {

  // if present, then it will ensure
  // that the broker is started first
  private final Broker broker;

  // if present, then it will ensure
  // that the gateway is started first
  private final Gateway gateway;

  public OperateModuleConfiguration(
      @Autowired(required = false) final Broker broker,
      @Autowired(required = false) final Gateway gateway) {
    this.broker = broker;
    this.gateway = gateway;
  }
}
