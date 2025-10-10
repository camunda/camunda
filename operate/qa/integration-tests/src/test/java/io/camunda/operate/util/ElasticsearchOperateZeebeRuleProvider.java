/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
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
import org.elasticsearch.client.indices.GetComponentTemplatesRequest;
import org.elasticsearch.client.indices.GetComponentTemplatesResponse;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.settings.Settings;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchOperateZeebeRuleProvider implements OperateZeebeRuleProvider {

  public static final String YYYY_MM_DD = "uuuu-MM-dd";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchOperateZeebeRuleProvider.class);
  @Autowired public OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  protected ZeebeContainer zeebeContainer;
  @Autowired private TestContainerUtil testContainerUtil;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  @Override
  public void starting(final Description description) {
    prefix = TestUtil.createRandomString(10);
    LOGGER.info("Starting Zeebe with ELS prefix: " + prefix);
    operateProperties.getZeebeElasticsearch().setPrefix(prefix);

    startZeebe();
  }

  @Override
  public void updateRefreshInterval(final String value) {
    try {
      final GetComponentTemplatesRequest getRequest =
          new GetComponentTemplatesRequest(prefix + "*");
      final GetComponentTemplatesResponse response =
          zeebeEsClient.cluster().getComponentTemplate(getRequest, RequestOptions.DEFAULT);
      response
          .getComponentTemplates()
          .entrySet()
          .forEach(
              componentTemplate -> {
                final Template template = componentTemplate.getValue().template();
                final Settings settings = template.settings();
                final PutComponentTemplateRequest request =
                    new PutComponentTemplateRequest().name(prefix);
                final Settings newSettings =
                    Settings.builder().put(settings).put("index.refresh_interval", value).build();
                final Template newTemplate = new Template(newSettings, template.mappings(), null);
                final ComponentTemplate newComponentTemplate =
                    new ComponentTemplate(newTemplate, null, null);
                request.componentTemplate(newComponentTemplate);
                try {
                  assertThat(
                          zeebeEsClient
                              .cluster()
                              .putComponentTemplate(request, RequestOptions.DEFAULT)
                              .isAcknowledged())
                      .isTrue();
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (final IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void refreshIndices(final Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
      final RefreshRequest refreshRequest = new RefreshRequest(prefix + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void finished(final Description description) {
    stopZeebe();
    if (client != null) {
      client.close();
      client = null;
    }
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, prefix);
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
        // retry
      } catch (final Exception e) {
        LOGGER.error("Topology cannot be retrieved.");
        e.printStackTrace();
        break;
        // exit
      }
    }
  }
}
