/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ZeebeVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.Template;
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
      GetComponentTemplatesRequest getRequest = new GetComponentTemplatesRequest(prefix);
      GetComponentTemplatesResponse response = zeebeEsClient.cluster().getComponentTemplate(getRequest, RequestOptions.DEFAULT);
      ComponentTemplate componentTemplate = response.getComponentTemplates().get(prefix);
      Settings settings = componentTemplate.template().settings();

      PutComponentTemplateRequest request = new PutComponentTemplateRequest().name(prefix);
      Settings newSettings = Settings.builder().put(settings).put("index.refresh_interval", value).build();
      Template newTemplate = new Template(newSettings, componentTemplate.template().mappings(), null);
      ComponentTemplate newComponentTemplate = new ComponentTemplate(newTemplate, null, null);
      request.componentTemplate(newComponentTemplate);
      assertThat(zeebeEsClient.cluster().putComponentTemplate(request, RequestOptions.DEFAULT).isAcknowledged()).isTrue();
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
