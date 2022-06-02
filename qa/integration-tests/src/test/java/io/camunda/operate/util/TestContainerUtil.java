/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.testcontainers.images.PullPolicy.alwaysPull;

import io.zeebe.containers.ZeebeContainer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.DockerImageName;

public class TestContainerUtil {

  private static final Logger logger = LoggerFactory.getLogger(TestContainerUtil.class);

  public static final String ELS_DOCKER_TESTCONTAINER_URL = "http://host.testcontainers.internal:9200";
  public static final String ELS_HOST = "localhost";
  public static final int ELS_PORT = 9200;
  public static final String ELS_SCHEME = "http";

  public static ZeebeContainer startZeebe(final String dataFolderPath, final String version,
      final String prefix, final Integer partitionCount) {
    long startTime = System.currentTimeMillis();
    Testcontainers.exposeHostPorts(ELS_PORT);
    final ZeebeContainer broker = new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + version));
    if (dataFolderPath != null) {
      broker.withFileSystemBind(dataFolderPath, "/usr/local/zeebe/data");
    }
    if (version.equals("SNAPSHOT")) {
      broker.withImagePullPolicy(alwaysPull());
    }
    broker.withEnv("JAVA_OPTS", "-Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Dzeebe.broker.exporters.elasticsearch.args.index.processMessageSubscription=true")
        .withEnv("ZEEBE_LOG_LEVEL", "ERROR")
        .withEnv("ATOMIX_LOG_LEVEL", "ERROR")
        .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", ELS_DOCKER_TESTCONTAINER_URL)
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME", "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", prefix)
        .withEnv("ZEEBE_BROKER_DATA_DISKUSAGEREPLICATIONWATERMARK", "0.99")
        .withEnv("ZEEBE_BROKER_DATA_DISKUSAGECOMMANDWATERMARK", "0.98")
        .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m");
    if (partitionCount != null) {
        broker.withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", String.valueOf(partitionCount));
    }
    broker.start();

    logger.info("\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));
    return broker;
  }

  public static ZeebeContainer startZeebe(final String version, final String prefix,
      final Integer partitionCount) {
    return startZeebe(null, version, prefix, partitionCount);
  }

  public static RestHighLevelClient getEsClient() {
    return new RestHighLevelClient(
        RestClient.builder(new HttpHost(ELS_HOST, ELS_PORT, ELS_SCHEME)));
  }

  public static void stopZeebe(final ZeebeContainer broker, final File tmpFolder) {
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
    }
  }

}
