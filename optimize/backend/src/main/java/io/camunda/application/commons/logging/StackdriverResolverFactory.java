/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.logging;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolverContext;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolverFactory;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverConfig;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverFactory;

@Plugin(name = "CamundaResolverFactory", category = TemplateResolverFactory.CATEGORY)
public final class StackdriverResolverFactory implements EventResolverFactory {

  private static final StackdriverResolverFactory INSTANCE = new StackdriverResolverFactory();

  private StackdriverResolverFactory() {}

  @PluginFactory
  public static StackdriverResolverFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public String getName() {
    return "stackdriver";
  }

  @Override
  public StackdriverResolver create(EventResolverContext context, TemplateResolverConfig config) {
    return new StackdriverResolver(config);
  }
}
