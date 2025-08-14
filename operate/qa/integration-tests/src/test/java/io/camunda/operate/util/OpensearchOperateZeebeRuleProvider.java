/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.componentTemplateRequestBuilder;
import static org.junit.Assert.assertTrue;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.runner.Description;
import org.opensearch.client.opensearch.cluster.ComponentTemplateSummary;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperateZeebeRuleProvider implements OperateZeebeRuleProvider {

  public static final String YYYY_MM_DD = "uuuu-MM-dd";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchOperateZeebeRuleProvider.class);
  @Autowired public OperateProperties operateProperties;

  @Autowired protected ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;
  protected ZeebeContainer zeebeContainer;
  @Autowired private TestContainerUtil testContainerUtil;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  @Override
  public void starting(final Description description) {
    prefix = TestUtil.createRandomString(10);
    LOGGER.info("Starting Zeebe with OS prefix: " + prefix);
    operateProperties.getZeebeOpensearch().setPrefix(prefix);

    startZeebe();
  }

  @Override
  public void updateRefreshInterval(final String value) {
    final ComponentTemplateSummary template =
        zeebeRichOpenSearchClient.template().getComponentTemplate().get(prefix).template();
    final IndexSettings indexSettings = template.settings().get("index");
    final IndexSettings newSettings =
        IndexSettings.of(b -> b.index(indexSettings).refreshInterval(ri -> ri.time(value)));
    final IndexState newTemplate =
        IndexState.of(t -> t.settings(newSettings).mappings(template.mappings()));
    final var requestBuilder = componentTemplateRequestBuilder(prefix).template(newTemplate);
    assertTrue(
        zeebeRichOpenSearchClient
            .template()
            .createComponentTemplateWithRetries(requestBuilder.build(), true));
  }

  @Override
  public void refreshIndices(final Instant instant) {
    final String date =
        DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
    zeebeRichOpenSearchClient.index().refresh(prefix + "*" + date);
  }

  @Override
  public void finished(final Description description) {
    stopZeebe();
    if (client != null) {
      client.close();
      client = null;
    }
    if (!failed) {
      TestUtil.removeAllIndices(
          zeebeRichOpenSearchClient.index(), zeebeRichOpenSearchClient.template(), prefix);
    }
  }

  @Override
  public void failed(final Throwable e, final Description description) {
    failed = true;
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  @Override
  public void startZeebe() {

    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    zeebeContainer =
        testContainerUtil.startZeebe(zeebeVersion, prefix, 2, isMultitTenancyEnabled());

    client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .usePlaintext()
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();

    testZeebeIsReady();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  @Override
  public void stopZeebe() {
    testContainerUtil.stopZeebe(null);
  }

  @Override
  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  @Override
  public ZeebeClient getClient() {
    return client;
  }

  @Override
  public boolean isMultitTenancyEnabled() {
    return operateProperties.getMultiTenancy().isEnabled();
  }

  private void testZeebeIsReady() {
    // get topology to check that cluster is available and ready for work
    Topology topology = null;
    while (topology == null) {
      try {
        topology = client.newTopologyRequest().send().join();
      } catch (final ClientException ex) {
        ex.printStackTrace();
      }
    }
  }
}
