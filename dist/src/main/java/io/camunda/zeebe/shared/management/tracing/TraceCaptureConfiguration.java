/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import io.atomix.cluster.AtomixCluster;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Wires the node-local bounded trace capture buffer, dynamic sampler wrapper, cluster-wide Atomix
 * RPC coordinator, and {@code /actuator/traces} endpoint on every broker and gateway node.
 */
@Configuration(proxyBeanMethods = false)
@NullMarked
public class TraceCaptureConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(TraceCaptureConfiguration.class);

  @Bean(destroyMethod = "close")
  public LocalTraceCapture localTraceCapture(
      final ObjectProvider<WebEndpointProperties> webEndpointPropertiesProvider) {
    final var properties = webEndpointPropertiesProvider.getIfAvailable();
    final String basePath =
        properties != null && properties.getBasePath() != null
            ? properties.getBasePath()
            : "/actuator";
    final String tracesPath = basePath.endsWith("/") ? basePath + "traces" : basePath + "/traces";
    return new LocalTraceCapture(tracesPath);
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public SdkTracerProviderBuilderCustomizer traceCaptureSdkTracerProviderCustomizer(
      final ObjectProvider<Sampler> samplerProvider, final LocalTraceCapture localTraceCapture) {
    return builder -> {
      var delegate = samplerProvider.getIfUnique();
      if (delegate == null) {
        LOG.warn(
            "No unique OpenTelemetry Sampler bean found; falling back to parentBased(alwaysOff) for"
                + " dynamic trace capture");
        delegate = Sampler.parentBased(Sampler.alwaysOff());
      }
      builder.setSampler(localTraceCapture.createSampler(delegate));
      builder.addSpanProcessor(localTraceCapture.spanProcessor());
      localTraceCapture.markSdkAttached();
    };
  }

  @Bean(destroyMethod = "close")
  public ClusterTraceCaptureService clusterTraceCaptureService(
      final LocalTraceCapture localTraceCapture, final AtomixCluster atomixCluster) {
    return new ClusterTraceCaptureService(
        localTraceCapture,
        atomixCluster.getMembershipService(),
        atomixCluster.getCommunicationService());
  }

  @Bean
  public TracesEndpoint tracesEndpoint(
      final ClusterTraceCaptureService clusterTraceCaptureService) {
    return new TracesEndpoint(clusterTraceCaptureService);
  }
}
