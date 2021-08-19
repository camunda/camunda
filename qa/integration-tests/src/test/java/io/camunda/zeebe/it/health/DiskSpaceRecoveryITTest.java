/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.health;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class DiskSpaceRecoveryITTest {
  private static final Logger LOG = LoggerFactory.getLogger(DiskSpaceRecoveryITTest.class);
  private static final String ELASTIC_HOSTNAME = "elastic";
  private static final String ELASTIC_HOST = "http://" + ELASTIC_HOSTNAME + ":9200";

  private Network network;
  private ZeebeVolume volume;
  private ZeebeContainer zeebeBroker;
  private ElasticsearchContainer elastic;
  private ZeebeClient client;

  @Before
  public void setUp() {
    final Map<String, String> volumeOptions =
        Map.of("type", "tmpfs", "device", "tmpfs", "o", "size=16m");
    volume = ZeebeVolume.newVolume(cmd -> cmd.withDriver("local").withDriverOpts(volumeOptions));

    network = Network.newNetwork();
    zeebeBroker = createZeebe().withZeebeData(volume).withNetwork(network);
    elastic = createElastic().withNetwork(network);
  }

  @After
  public void tearDown() {
    if (zeebeBroker != null) {
      CloseHelper.quietClose(zeebeBroker);
    }

    if (elastic != null) {
      CloseHelper.quietClose(elastic);
    }

    if (network != null) {
      CloseHelper.quietClose(network);
    }

    if (volume != null) {
      CloseHelper.quietClose(volume);
    }
  }

  @Test
  public void shouldRecoverAfterOutOfDiskSpaceWhenExporterStarts() {
    // given
    zeebeBroker.start();
    client = createClient();

    // when
    LOG.info("Wait until broker is out of disk space");
    await()
        .timeout(Duration.ofMinutes(3))
        .pollInterval(1, TimeUnit.MICROSECONDS)
        .untilAsserted(
            () ->
                assertThatThrownBy(this::publishMessage)
                    .hasRootCauseMessage(
                        "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space"));

    LOG.info("Start Elastic and wait until broker compacts to make more space");
    elastic.start();

    // then
    await()
        .pollInterval(Duration.ofSeconds(10))
        .timeout(Duration.ofMinutes(3))
        .untilAsserted(() -> assertThatCode(this::publishMessage).doesNotThrowAnyException());
  }

  @Test
  public void shouldNotProcessWhenOutOfDiskSpaceOnStart() {
    // given
    zeebeBroker.withEnv("ZEEBE_BROKER_DATA_DISKUSAGECOMMANDWATERMARK", "0.0001");

    // when
    zeebeBroker.start();
    client = createClient();

    // then
    assertThatThrownBy(this::publishMessage)
        .hasRootCauseMessage(
            "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space");
  }

  private ZeebeClient createClient() {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(zeebeBroker.getExternalGatewayAddress())
        .usePlaintext()
        .build();
  }

  private void publishMessage() {
    client
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey(String.valueOf(1))
        .send()
        .join();
  }

  private ZeebeContainer createZeebe() {
    return new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", ELASTIC_HOST)
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
        .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "1MB")
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
        .withEnv("ZEEBE_BROKER_DATA_DISKUSAGECOMMANDWATERMARK", "0.5")
        .withEnv("ZEEBE_BROKER_DATA_LOGINDEXDENSITY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_MESSAGE", "true");
  }

  private ElasticsearchContainer createElastic() {
    final var version = RestClient.class.getPackage().getImplementationVersion();
    final var image =
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag(version);

    return new ElasticsearchContainer(image)
        .withEnv("discovery.type", "single-node")
        .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g -XX:MaxDirectMemorySize=1073741824")
        .withNetworkAliases(ELASTIC_HOSTNAME);
  }
}
