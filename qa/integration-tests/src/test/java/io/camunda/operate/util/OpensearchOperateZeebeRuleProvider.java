/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import org.junit.runner.Description;
import org.opensearch.client.opensearch.cluster.ComponentTemplateSummary;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.componentTemplateRequestBuilder;
import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static org.junit.Assert.assertTrue;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperateZeebeRuleProvider implements OperateZeebeRuleProvider {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

  private static final Logger logger = LoggerFactory.getLogger(OpensearchOperateZeebeRuleProvider.class);
  public static final String YYYY_MM_DD = "uuuu-MM-dd";

  @Autowired
  public OperateProperties operateProperties;

  @Autowired
  protected ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;

  @Autowired
  private static TestContainerUtil testContainerUtil;

  protected ZeebeContainer zeebeContainer;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    logger.info("Starting Zeebe with OS prefix: " + prefix);
    operateProperties.getZeebeOpensearch().setPrefix(prefix);

    startZeebe();
  }

  public void updateRefreshInterval(String value) {
    ComponentTemplateSummary template = zeebeRichOpenSearchClient.template().getComponentTemplate().get(prefix).template();
    IndexSettings indexSettings = template.settings().get("index");
    IndexSettings newSettings = IndexSettings.of(b -> b.index(indexSettings).refreshInterval(ri -> ri.time(value)));
    IndexState newTemplate = IndexState.of(t -> t.settings(newSettings).mappings(template.mappings()));
    var requestBuilder = componentTemplateRequestBuilder(prefix).template(newTemplate);
    assertTrue(zeebeRichOpenSearchClient.template().createComponentTemplateWithRetries(requestBuilder.build()));
  }

  public void refreshIndices(Instant instant) {
    String date = DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
    zeebeRichOpenSearchClient.index().refresh(prefix + "*" + date);
  }

  @Override
  public void finished(Description description) {
    stop();
    if (!failed) {
      TestUtil.removeAllIndices(zeebeRichOpenSearchClient.index(), zeebeRichOpenSearchClient.template(), prefix);
    }
  }

  @Override
  public void failed(Throwable e, Description description) {
    this.failed = true;
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void startZeebe() {

    final String zeebeVersion = ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    zeebeContainer = testContainerUtil.startZeebe(zeebeVersion, prefix, 2, isMultitTenancyEnabled());

    client = ZeebeClient.newClientBuilder()
        .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
        .usePlaintext()
        .defaultRequestTimeout(REQUEST_TIMEOUT)
    .build();

    testZeebeIsReady();

  }

  private void testZeebeIsReady() {
    //get topology to check that cluster is available and ready for work
    Topology topology = null;
    while (topology == null) {
      try {
        topology = client.newTopologyRequest().send().join();
      } catch (ClientException ex) {
        ex.printStackTrace();
      }
    }
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    zeebeContainer.stop();

    if (client != null) {
      client.close();
      client = null;
    }
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ZeebeClient getClient() {
    return client;
  }

  @Override
  public boolean isMultitTenancyEnabled() {
    return operateProperties.getMultiTenancy().isEnabled();
  }
}
