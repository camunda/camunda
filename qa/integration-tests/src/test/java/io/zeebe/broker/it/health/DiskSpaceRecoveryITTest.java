/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.health;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import io.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class DiskSpaceRecoveryITTest {
  static ZeebeContainer zeebeBroker;
  private static final Logger LOG = LoggerFactory.getLogger(DiskSpaceRecoveryITTest.class);
  private static final String VOLUME_NAME = "data-DiskSpaceRecoveryITTest";
  private static ElasticsearchContainer elastic;
  private static final String ELASTIC_HOST = "http://elastic:9200";
  private ZeebeClient client;

  @Before
  public void setUp() {
    zeebeBroker = new ZeebeContainer("camunda/zeebe:current-test");
    configureZeebe(zeebeBroker);
    final var network = zeebeBroker.getNetwork();
    elastic = createElastic(network);
  }

  private static ZeebeContainer configureZeebe(final ZeebeContainer zeebeBroker) {

    zeebeBroker
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", ELASTIC_HOST)
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
        .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "1MB")
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
        .withEnv("ZEEBE_BROKER_DATA_DISKUSAGECOMMANDWATERMARK", "0.5")
        .withEnv("ZEEBE_BROKER_DATA_LOGINDEXDENSITY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_MESSAGE", "true");

    zeebeBroker
        .getDockerClient()
        .createVolumeCmd()
        .withDriver("local")
        .withDriverOpts(Map.of("type", "tmpfs", "device", "tmpfs", "o", "size=16m"))
        .withName(VOLUME_NAME)
        .exec();
    final Volume newVolume = new Volume("/usr/local/zeebe/data");
    zeebeBroker.withCreateContainerCmdModifier(
        cmd ->
            cmd.withHostConfig(cmd.getHostConfig().withBinds(new Bind(VOLUME_NAME, newVolume)))
                .withName("zeebe-test"));

    return zeebeBroker;
  }

  private static ElasticsearchContainer createElastic(final Network network) {
    final ElasticsearchContainer container =
        new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:"
                + RestClient.class.getPackage().getImplementationVersion());

    container.withNetwork(network).withEnv("discovery.type", "single-node");
    container.withCreateContainerCmdModifier(cmd -> cmd.withName("elastic"));
    return container;
  }

  @After
  public void tearDown() {
    zeebeBroker.stop();
    zeebeBroker.getDockerClient().removeVolumeCmd(VOLUME_NAME).exec();
    elastic.stop();
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

    elastic.start();

    // then
    await()
        .pollInterval(Duration.ofSeconds(10))
        .timeout(Duration.ofMinutes(3))
        .untilAsserted(() -> assertThatCode(this::publishMessage).doesNotThrowAnyException());
  }

  private ZeebeClient createClient() {
    final var apiPort = zeebeBroker.getMappedPort(26500);
    final var containerIPAddress = zeebeBroker.getContainerIpAddress();
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(containerIPAddress + ":" + apiPort)
        .usePlaintext()
        .build();
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

  private void publishMessage() {
    client
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey(String.valueOf(1))
        .send()
        .join();
  }
}
