/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command-line entry point for {@link ZeebeProcessDataGenerator}.
 *
 * <p>This class has one responsibility: parse CLI arguments and construct the Elasticsearch client,
 * then delegate data generation to {@link ZeebeProcessDataGenerator}. Connection setup and argument
 * parsing are the only reasons this class should change.
 *
 * <pre>
 * Options:
 *   --host         Elasticsearch host            (default: localhost)
 *   --port         Elasticsearch HTTP port       (default: 9200)
 *   --username     Basic-auth username           (default: none)
 *   --password     Basic-auth password           (default: none)
 *   --prefix       Zeebe record index prefix     (default: zeebe-record)
 *   --instances    Number of process instances   (default: 10000)
 *   --defs         Number of process definitions (default: 6)
 *   --months       Months of history to cover    (default: 6)
 *   --seed         RNG seed for reproducibility  (default: 42)
 *   --batch-size   ES bulk-request batch size    (default: 1000)
 * </pre>
 */
public final class ZeebeDataGeneratorCli {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeDataGeneratorCli.class);

  private ZeebeDataGeneratorCli() {}

  public static void main(final String[] args) {
    String host = "localhost";
    int port = 9200;
    String username = null;
    String password = null;
    String prefix = "zeebe-record";
    int instances = 300000;
    int defs = 6;
    int months = 6;
    long seed = 42L;
    int batchSize = 1_000;
    double updateRate = 0.25;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--host" -> host = args[++i];
        case "--port" -> port = Integer.parseInt(args[++i]);
        case "--username" -> username = args[++i];
        case "--password" -> password = args[++i];
        case "--prefix" -> prefix = args[++i];
        case "--instances" -> instances = Integer.parseInt(args[++i]);
        case "--defs" -> defs = Integer.parseInt(args[++i]);
        case "--months" -> months = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--batch-size" -> batchSize = Integer.parseInt(args[++i]);
        case "--update-rate" -> updateRate = Double.parseDouble(args[++i]);
        default -> LOG.warn("Unknown argument '{}' — ignored", args[i]);
      }
    }

    LOG.info("Connecting to Elasticsearch at {}:{} with prefix '{}'", host, port, prefix);

    final ElasticsearchConnection connection =
        new ElasticsearchConnection(host, port, username, password);
    final RestClient restClient = buildRestClient(connection);
    final ElasticsearchClient rawClient =
        new ElasticsearchClient(
            new RestClientTransport(
                restClient, new JacksonJsonpMapper(ObjectMapperFactory.OPTIMIZE_MAPPER)));
    final OptimizeElasticsearchClient esClient = buildEsClient(restClient, prefix);

    final long positionOffset = queryMaxPosition(rawClient, prefix) + 1;
    final long instanceKeyOffset = queryMaxInstanceKey(rawClient, prefix) + 1;
    LOG.info(
        "Continuing from positionOffset={}, instanceKeyOffset={}",
        positionOffset,
        instanceKeyOffset);

    final GeneratorConfig config =
        new GeneratorConfig.Builder()
            .zeebeRecordPrefix(prefix)
            .instanceCount(instances)
            .processDefinitionCount(defs)
            .monthsOfHistory(months)
            .seed(seed)
            .batchSize(batchSize)
            .positionOffset(positionOffset)
            .instanceKeyOffset(instanceKeyOffset)
            .updateRate(updateRate)
            .build();

    LOG.info(
        "Generating {} instances across {} process definitions, {} months of history",
        instances,
        defs,
        months);
    new ZeebeProcessDataGenerator(config).generate(esClient);
    LOG.info("Generation complete.");

    try {
      restClient.close();
    } catch (final IOException e) {
      LOG.warn("Failed to close REST client cleanly", e);
    }
  }

  /**
   * Returns the highest {@code position} value found across all Zeebe record indexes for the given
   * prefix, or {@code 0} if the indexes are empty or do not exist yet.
   */
  private static long queryMaxPosition(final ElasticsearchClient esClient, final String prefix) {
    final List<String> indexes =
        List.of(
            prefix + "-" + ZEEBE_PROCESS_DEFINITION_INDEX_NAME,
            prefix + "-" + ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
            prefix + "-" + ZEEBE_VARIABLE_INDEX_NAME,
            prefix + "-" + ZEEBE_USER_TASK_INDEX_NAME,
            prefix + "-" + ZEEBE_INCIDENT_INDEX_NAME);
    long maxPosition = 0L;
    for (final String index : indexes) {
      try {
        final var response =
            esClient.search(
                s ->
                    s.index(index)
                        .size(0)
                        .aggregations("max_pos", a -> a.max(m -> m.field("position"))),
                Void.class);
        final double value = response.aggregations().get("max_pos").max().value();
        if (Double.isFinite(value)) {
          maxPosition = Math.max(maxPosition, (long) value);
        }
      } catch (final Exception e) {
        LOG.debug(
            "Could not query max position from '{}' (may not exist yet): {}",
            index,
            e.getMessage());
      }
    }
    return maxPosition;
  }

  /**
   * Returns the highest {@code value.processInstanceKey} found in the process-instance index, or
   * {@code 3_000_000_000L - 1} (so the default offset is preserved) when the index is empty.
   */
  private static long queryMaxInstanceKey(final ElasticsearchClient esClient, final String prefix) {
    final String index = prefix + "-" + ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
    try {
      final var response =
          esClient.search(
              s ->
                  s.index(index)
                      .size(0)
                      .aggregations(
                          "max_key", a -> a.max(m -> m.field("value.processInstanceKey"))),
              Void.class);
      final double value = response.aggregations().get("max_key").max().value();
      if (Double.isFinite(value)) {
        return (long) value;
      }
    } catch (final Exception e) {
      LOG.debug(
          "Could not query max instance key from '{}' (may not exist yet): {}",
          index,
          e.getMessage());
    }
    return GeneratorConfig.Builder.DEFAULT_INSTANCE_KEY_OFFSET - 1;
  }

  static RestClient buildRestClient(final ElasticsearchConnection connection) {
    final org.elasticsearch.client.RestClientBuilder builder =
        RestClient.builder(new HttpHost(connection.host(), connection.port(), "http"));
    if (connection.username() != null) {
      final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(connection.username(), connection.password()));
      builder.setHttpClientConfigCallback(
          b -> b.setDefaultCredentialsProvider(credentialsProvider));
    }
    return builder.build();
  }

  static OptimizeElasticsearchClient buildEsClient(
      final RestClient restClient, final String prefix) {
    final ObjectMapper mapper = ObjectMapperFactory.OPTIMIZE_MAPPER;
    final ElasticsearchClient esClient =
        new ElasticsearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper(mapper)));
    return new OptimizeElasticsearchClient(
        restClient, mapper, esClient, new OptimizeIndexNameService(prefix));
  }

  /** Groups the Elasticsearch connection parameters passed via CLI arguments. */
  record ElasticsearchConnection(String host, int port, String username, String password) {}
}
