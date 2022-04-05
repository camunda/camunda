/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ZeebeVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;

import io.camunda.operate.property.OperateProperties;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.elasticsearch.client.indices.GetIndexTemplatesResponse;
import org.elasticsearch.client.indices.IndexTemplateMetadata;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class OperateZeebeRule extends TestWatcher {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

  private static final Logger logger = LoggerFactory.getLogger(OperateZeebeRule.class);
  public static final String YYYY_MM_DD = "uuuu-MM-dd";

  @Autowired
  public OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  protected ZeebeContainer zeebeContainer;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    logger.info("Starting Zeebe with ELS prefix: " + prefix);
    operateProperties.getZeebeElasticsearch().setPrefix(prefix);

    startZeebe();
  }

  public void updateRefreshInterval(String value) {
    try {
      GetIndexTemplatesRequest request = new GetIndexTemplatesRequest(prefix);
      GetIndexTemplatesResponse indexTemplate = zeebeEsClient.indices().getIndexTemplate(request, RequestOptions.DEFAULT);
      IndexTemplateMetadata indexTemplateMetaData = indexTemplate.getIndexTemplates().get(0);

      PutIndexTemplateRequest updateTemplateRequest = new PutIndexTemplateRequest(prefix);
      updateTemplateRequest.patterns(indexTemplateMetaData.patterns());
      updateTemplateRequest.order(indexTemplateMetaData.order());
      updateTemplateRequest.settings(Settings.builder().put(indexTemplateMetaData.settings()).put("index.refresh_interval", value));
      updateTemplateRequest.alias(new Alias(prefix));
      updateTemplateRequest.mapping(indexTemplateMetaData.mappings().getSourceAsMap());
      zeebeEsClient.indices().putTemplate(updateTemplateRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void refreshIndices(Instant instant) {
    try {
      String date = DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
      RefreshRequest refreshRequest = new RefreshRequest(prefix + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void finished(Description description) {
    stop();
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, prefix);
    }
  }

  @Override
  protected void failed(Throwable e, Description description) {
    this.failed = true;
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void startZeebe() {

    final String zeebeVersion = ZeebeVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    zeebeContainer = TestContainerUtil.startZeebe(zeebeVersion, prefix, 2);

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

  public void setOperateProperties(final OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
  }

  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {
    this.zeebeEsClient = zeebeEsClient;
  }

  public ZeebeClient getClient() {
    return client;
  }
}
