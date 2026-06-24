/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.logging;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolverContext;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolverFactory;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverConfig;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverFactory;

@Plugin(name = "OptimizeResolverFactory", category = TemplateResolverFactory.CATEGORY)
public final class OptimizeStackdriverResolverFactory implements EventResolverFactory {

  private static final OptimizeStackdriverResolverFactory INSTANCE =
      new OptimizeStackdriverResolverFactory();

  private OptimizeStackdriverResolverFactory() {}

  @PluginFactory
  public static OptimizeStackdriverResolverFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public String getName() {
    return "optimizeStackdriver";
  }

  @Override
  public OptimizeStackdriverResolver create(
      final EventResolverContext context, final TemplateResolverConfig config) {
    return new OptimizeStackdriverResolver(config);
  }
}
