/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Histogram;
import io.zeebe.exporter.dto.BulkItemError;
import io.zeebe.exporter.dto.BulkResponse;
import io.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;

public class ElasticsearchClient {

  public static final String INDEX_TEMPLATE_FILENAME_PATTERN = "/zeebe-record-%s-template.json";
  public static final String INDEX_DELIMITER = "_";
  public static final String ALIAS_DELIMITER = "-";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  protected final RestClient client;
  private final ElasticsearchExporterConfiguration configuration;
  private final Logger log;
  private final DateTimeFormatter formatter;
  private List<String> bulkRequest;
  private ElasticsearchMetrics metrics;

  public ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration, final Logger log) {
    this(configuration, log, new ArrayList<>());
  }

  ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration,
      final Logger log,
      final List<String> bulkRequest) {
    this.configuration = configuration;
    this.log = log;
    client = createClient();
    this.bulkRequest = bulkRequest;
    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  }

  public void close() throws IOException {
    client.close();
  }

  public void index(final Record<?> record) {
    if (metrics == null) {
      metrics = new ElasticsearchMetrics(record.getPartitionId());
    }

    checkRecord(record);
    bulk(newIndexCommand(record), record);
  }

  private void checkRecord(final Record<?> record) {
    if (record.getValueType() == ValueType.VARIABLE) {
      checkVariableRecordValue((Record<VariableRecordValue>) record);
    }
  }

  private void checkVariableRecordValue(final Record<VariableRecordValue> record) {
    final VariableRecordValue value = record.getValue();
    final int size = value.getValue().getBytes().length;

    if (size > configuration.index.ignoreVariablesAbove) {
      log.warn(
          "Variable {key: {}, name: {}, variableScope: {}, processInstanceKey: {}} exceeded max size of {} bytes with a size of {} bytes. As a consequence this variable is not index by elasticsearch.",
          record.getKey(),
          value.getName(),
          value.getScopeKey(),
          value.getProcessInstanceKey(),
          configuration.index.ignoreVariablesAbove,
          size);
    }
  }

  public void bulk(final Map<String, Object> command, final Record<?> record) {
    final String serializedCommand;

    try {
      serializedCommand = MAPPER.writeValueAsString(command);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to serialize bulk request command to JSON", e);
    }

    final String jsonCommand = serializedCommand + "\n" + record.toJson();
    // don't re-append when retrying same record, to avoid OOM
    if (bulkRequest.isEmpty() || !bulkRequest.get(bulkRequest.size() - 1).equals(jsonCommand)) {
      bulkRequest.add(jsonCommand);
    }
  }

  /**
   * @throws ElasticsearchExporterException if not all items of the bulk were flushed successfully
   */
  public void flush() {
    if (bulkRequest.isEmpty()) {
      return;
    }

    final int bulkSize = bulkRequest.size();
    metrics.recordBulkSize(bulkSize);

    final var bulkMemorySize = getBulkMemorySize();
    metrics.recordBulkMemorySize(bulkMemorySize);

    final BulkResponse bulkResponse;
    try {
      bulkResponse = exportBulk();

    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to flush bulk", e);
    }

    final var success = checkBulkResponse(bulkResponse);
    if (!success) {
      throw new ElasticsearchExporterException("Failed to flush all items of the bulk");
    }

    // all records where flushed, create new bulk request, otherwise retry next time
    bulkRequest = new ArrayList<>();
  }

  private boolean checkBulkResponse(final BulkResponse bulkResponse) {
    final var hasErrors = bulkResponse.hasErrors();
    if (hasErrors) {
      bulkResponse.getItems().stream()
          .flatMap(item -> Optional.ofNullable(item.getIndex()).stream())
          .flatMap(index -> Optional.ofNullable(index.getError()).stream())
          .collect(Collectors.groupingBy(BulkItemError::getType))
          .forEach(
              (errorType, errors) ->
                  log.warn(
                      "Failed to flush {} item(s) of bulk request [type: {}, reason: {}]",
                      errors.size(),
                      errorType,
                      errors.get(0).getReason()));
    }

    return !hasErrors;
  }

  private BulkResponse exportBulk() throws IOException {
    try (final Histogram.Timer timer = metrics.measureFlushDuration()) {
      final var request = new Request("POST", "/_bulk");
      request.setJsonEntity(String.join("\n", bulkRequest) + "\n");

      final var response = client.performRequest(request);

      return MAPPER.readValue(response.getEntity().getContent(), BulkResponse.class);
    }
  }

  public boolean shouldFlush() {
    return bulkRequest.size() >= configuration.bulk.size
        || getBulkMemorySize() >= configuration.bulk.memoryLimit;
  }

  private int getBulkMemorySize() {
    return bulkRequest.stream().mapToInt(String::length).sum();
  }

  /** @return true if request was acknowledged */
  public boolean putIndexTemplate(final ValueType valueType) {
    final String templateName = indexPrefixForValueType(valueType);
    final String aliasName = aliasNameForValueType(valueType);
    final String filename = indexTemplateForValueType(valueType);
    return putIndexTemplate(templateName, aliasName, filename);
  }

  /** @return true if request was acknowledged */
  public boolean putIndexTemplate(
      final String templateName, final String aliasName, final String filename) {
    final Map<String, Object> template;
    try (final InputStream inputStream =
        ElasticsearchExporter.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        template = convertToMap(XContentType.JSON.xContent(), inputStream);
      } else {
        throw new ElasticsearchExporterException(
            "Failed to find index template in classpath " + filename);
      }
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load index template from classpath " + filename, e);
    }

    // update prefix in template in case it was changed in configuration
    template.put("index_patterns", Collections.singletonList(templateName + INDEX_DELIMITER + "*"));

    // update alias in template in case it was changed in configuration
    final Object templateProperties =
        template.computeIfAbsent("template", key -> new HashMap<String, Object>());
    if (templateProperties instanceof Map) {
      final Map templatePropertyMap = (Map) templateProperties;
      templatePropertyMap.put(
          "aliases", Collections.singletonMap(aliasName, Collections.emptyMap()));
    } else {
      throw new IllegalStateException(
          String.format(
              "Expected the 'template' field of the index template '%s' to be a map, but was '%s'",
              templateName, templateProperties.getClass()));
    }

    return putIndexTemplate(templateName, template);
  }

  /** @return true if request was acknowledged */
  private boolean putIndexTemplate(final String templateName, final Object body) {
    try {
      final var request = new Request("PUT", "/_index_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(body));

      final var response = client.performRequest(request);
      final var putIndexTemplateResponse =
          MAPPER.readValue(response.getEntity().getContent(), PutIndexTemplateResponse.class);
      return putIndexTemplateResponse.isAcknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  private RestClient createClient() {
    final HttpHost[] httpHosts = urlsToHttpHosts(configuration.url);
    final RestClientBuilder builder =
        RestClient.builder(httpHosts)
            .setHttpClientConfigCallback(this::setHttpClientConfigCallback);

    return builder.build();
  }

  private HttpAsyncClientBuilder setHttpClientConfigCallback(final HttpAsyncClientBuilder builder) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (configuration.hasAuthenticationPresent()) {
      setupBasicAuthentication(builder);
    }

    return builder;
  }

  private void setupBasicAuthentication(final HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            configuration.getAuthentication().getUsername(),
            configuration.getAuthentication().getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private static HttpHost[] urlsToHttpHosts(final String urls) {
    return Arrays.stream(urls.split(","))
        .map(String::trim)
        .map(ElasticsearchClient::urlToHttpHost)
        .toArray(HttpHost[]::new);
  }

  private static HttpHost urlToHttpHost(final String url) {
    final URI uri;
    try {
      uri = new URI(url);
    } catch (final URISyntaxException e) {
      throw new ElasticsearchExporterException("Failed to parse url " + url, e);
    }

    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  protected String indexFor(final Record<?> record) {
    final Instant timestamp = Instant.ofEpochMilli(record.getTimestamp());
    return indexPrefixForValueTypeWithDelimiter(record.getValueType())
        + formatter.format(timestamp);
  }

  protected String idFor(final Record<?> record) {
    return record.getPartitionId() + "-" + record.getPosition();
  }

  protected String typeFor(final Record<?> record) {
    return "_doc";
  }

  protected String indexPrefixForValueTypeWithDelimiter(final ValueType valueType) {
    return indexPrefixForValueType(valueType) + INDEX_DELIMITER;
  }

  private String aliasNameForValueType(final ValueType valueType) {
    return configuration.index.prefix + ALIAS_DELIMITER + valueTypeToString(valueType);
  }

  private String indexPrefixForValueType(final ValueType valueType) {
    final String version = VersionUtil.getVersionLowerCase();
    return configuration.index.prefix
        + INDEX_DELIMITER
        + valueTypeToString(valueType)
        + INDEX_DELIMITER
        + version;
  }

  private static String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replaceAll("_", "-");
  }

  private static String indexTemplateForValueType(final ValueType valueType) {
    return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
  }

  private Map<String, Object> convertToMap(final XContent content, final InputStream input) {
    try (XContentParser parser =
        content.createParser(
            NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, input)) {
      return parser.mapOrdered();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to parse content to map", e);
    }
  }

  private Map<String, Object> newIndexCommand(final Record<?> record) {
    final Map<String, Object> command = new HashMap<>();
    final Map<String, Object> contents = new HashMap<>();
    contents.put("_index", indexFor(record));
    contents.put("_id", idFor(record));
    contents.put("routing", String.valueOf(record.getPartitionId()));

    command.put("index", contents);
    return command;
  }
}
