/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.severalversions;

import static org.camunda.operate.util.ThreadUtil.sleepFor;

import io.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.MountableFile;

public class TestContainerUtil {

  private static final Logger logger = LoggerFactory.getLogger(TestContainerUtil.class);

  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch-oss";
  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final int ELS_PORT = 9200;
  public static final String ZEEBE_CFG_YAML_FILE = "/severalversions/application.yaml";
  public static final String PROPERTIES_PREFIX = "camunda.operate.";

  private Network network;
  private ZeebeBrokerContainer broker;
  private ElasticsearchContainer elsContainer;
  private ZeebeClient client;

  private String gatewayAddress;
  private String elsHost;
  private Integer elsPort;
  private RestHighLevelClient esClient;

  public void startZeebe(final String dataFolderPath, final String version) {
    broker = new ZeebeBrokerContainer("camunda/zeebe:" + version)
        .withFileSystemBind(dataFolderPath, "/usr/local/zeebe/data")
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        .withNetwork(getNetwork());
    addConfig(broker, version);
    broker.start();

    gatewayAddress = broker.getExternalAddress(ZeebePort.GATEWAY.getPort());
    client = ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build();
  }

  private void addConfig(ZeebeBrokerContainer broker, String version) {
    broker.withCopyFileToContainer(MountableFile.forClasspathResource(ZEEBE_CFG_YAML_FILE), "/usr/local/zeebe/config/application.yaml");
  }

  public void startElasticsearch() {
    elsContainer = new ElasticsearchContainer(String.format("%s:%s", DOCKER_ELASTICSEARCH_IMAGE_NAME, ElasticsearchClient.class.getPackage().getImplementationVersion()))
        .withNetwork(getNetwork())
        .withNetworkAliases(ELS_NETWORK_ALIAS)
        .withExposedPorts(ELS_PORT);
    elsContainer.start();
    elsHost = elsContainer.getContainerIpAddress();
    elsPort = elsContainer.getMappedPort(ELS_PORT);
    logger.info(String.format("Elasticsearch started on %s:%s", elsHost, elsPort));
  }

  public String[] getOperateProperties() {
    return new String[] {
        PROPERTIES_PREFIX + "elasticsearch.host=" + elsHost,
        PROPERTIES_PREFIX + "elasticsearch.port=" + elsPort,
        PROPERTIES_PREFIX + "zeebeElasticsearch.host=" + elsHost,
        PROPERTIES_PREFIX + "zeebeElasticsearch.port=" + elsPort,
        PROPERTIES_PREFIX + "zeebeElasticsearch.prefix=" + ImportSeveralVersionsInitializer.ZEEBE_PREFIX,
        PROPERTIES_PREFIX + "zeebe.gatewayAddress=" + gatewayAddress,
        PROPERTIES_PREFIX + "importer.startLoadingDataOnStartup=false"
    };
  }

  public RestHighLevelClient getEsClient() {
    if (esClient == null) {
      esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(elsHost, elsPort, "http")));
    }
    return esClient;
  }

  private Network getNetwork() {
    if (network == null) {
      network = Network.newNetwork();
    }
    return network;
  }

  public void stopAll() {
    stopZeebe(null);
    stopEls();
    closeNetwork();
  }

  public void stopZeebe(File tmpFolder) {
    if (client != null) {
      client.close();
      client = null;
    }
    if (broker != null) {
      try {
        if (tmpFolder != null && tmpFolder.listFiles().length > 0) {
          boolean found = false;
          int attempts = 0;
          while (!found && attempts < 10) {
            //check for snapshot existence
            final List<Path> files = Files.walk(Paths.get(tmpFolder.toURI()))
                .filter(p -> p.getFileName().endsWith("snapshots"))
                .collect(Collectors.toList());
            if (files.size() == 1 && Files.isDirectory(files.get(0))) {
              if (Files.walk(files.get(0)).count() > 1) {
                found = true;
                logger.debug("Zeebe snapshot was found in " + Files.walk(files.get(0)).findFirst().toString());
              }
            }
            if (!found) {
              sleepFor(10000L);
            }
            attempts++;
          }
          if (!found) {
            throw new AssertionError("Zeebe snapshot was never taken");
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      broker.shutdownGracefully(Duration.ofSeconds(3));
      broker = null;
    }
  }

  private void stopEls() {
    if (elsContainer != null) {
      elsContainer.stop();
    }
  }

  private void closeNetwork(){
    if (network != null) {
      network.close();
      network = null;
    }
  }

  public ZeebeClient getClient() {
    return client;
  }

}
