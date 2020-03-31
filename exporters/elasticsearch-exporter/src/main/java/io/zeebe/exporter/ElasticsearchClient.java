/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import io.prometheus.client.Histogram;
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
import java.util.Collections;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;

public class ElasticsearchClient {

  public static final String INDEX_TEMPLATE_FILENAME_PATTERN = "/zeebe-record-%s-template.json";
  public static final String INDEX_DELIMITER = "_";
  public static final String ALIAS_DELIMITER = "-";
  protected final RestHighLevelClient client;
  private final ElasticsearchExporterConfiguration configuration;
  private final Logger log;
  private final DateTimeFormatter formatter;
  private BulkRequest bulkRequest;
  private ElasticsearchMetrics metrics;

  public ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration, final Logger log) {
    this.configuration = configuration;
    this.log = log;
    this.client = createClient();
    this.bulkRequest = new BulkRequest();
    this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  }

  public void close() throws IOException {
    client.close();
  }

  public void index(final Record<?> record) {
    if (metrics == null) {
      metrics = new ElasticsearchMetrics(record.getPartitionId());
    }

    checkRecord(record);

    final IndexRequest request =
        new IndexRequest(indexFor(record), typeFor(record), idFor(record))
            .source(record.toJson(), XContentType.JSON)
            .routing(String.valueOf(record.getPartitionId()));
    bulk(request);
  }

  @SuppressWarnings("unchecked")
  private void checkRecord(Record<?> record) {
    if (record.getValueType() == ValueType.VARIABLE) {
      checkVariableRecordValue((Record<VariableRecordValue>) record);
    }
  }

  private void checkVariableRecordValue(Record<VariableRecordValue> record) {
    final VariableRecordValue value = record.getValue();
    final int size = value.getValue().getBytes().length;

    if (size > configuration.index.ignoreVariablesAbove) {
      log.warn(
          "Variable {key: {}, name: {}, variableScope: {}, workflowInstanceKey: {}} exceeded max size of {} bytes with a size of {} bytes. As a consequence this variable is not index by elasticsearch.",
          record.getKey(),
          value.getName(),
          value.getScopeKey(),
          value.getWorkflowInstanceKey(),
          configuration.index.ignoreVariablesAbove,
          size);
    }
  }

  public void bulk(final IndexRequest indexRequest) {
    bulkRequest.add(indexRequest);
  }

  /** @return true if all bulk records where flushed successfully */
  public boolean flush() {
    boolean success = true;
    final int bulkSize = bulkRequest.numberOfActions();
    if (bulkSize > 0) {
      try {
        metrics.recordBulkSize(bulkSize);
        final BulkResponse responses = exportBulk();
        success = checkBulkResponses(responses);
      } catch (final IOException e) {
        throw new ElasticsearchExporterException("Failed to flush bulk", e);
      }

      if (success) {
        // all records where flushed, create new bulk request, otherwise retry next time
        bulkRequest = new BulkRequest();
      }
    }

    return success;
  }

  private BulkResponse exportBulk() throws IOException {
    try (final Histogram.Timer timer = metrics.measureFlushDuration()) {
      return client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }
  }

  private boolean checkBulkResponses(final BulkResponse responses) {
    for (final BulkItemResponse response : responses) {
      if (response.isFailed()) {
        log.warn("Failed to flush at least one bulk request {}", response.getFailureMessage());
        return false;
      }
    }

    return true;
  }

  public boolean shouldFlush() {
    return bulkRequest.numberOfActions() >= configuration.bulk.size;
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
        template = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
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
    template.put("aliases", Collections.singletonMap(aliasName, Collections.EMPTY_MAP));

    final PutIndexTemplateRequest request =
        new PutIndexTemplateRequest(templateName).source(template);

    return putIndexTemplate(request);
  }

  /** @return true if request was acknowledged */
  private boolean putIndexTemplate(final PutIndexTemplateRequest putIndexTemplateRequest) {
    try {
      return client
          .indices()
          .putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT)
          .isAcknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  private RestHighLevelClient createClient() {
    final HttpHost httpHost = urlToHttpHost(configuration.url);

    // use single thread for rest client
    final RestClientBuilder builder =
        RestClient.builder(httpHost).setHttpClientConfigCallback(this::setHttpClientConfigCallback);

    return new RestHighLevelClient(builder);
  }

  private HttpAsyncClientBuilder setHttpClientConfigCallback(final HttpAsyncClientBuilder builder) {
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (configuration.authentication.isPresent()) {
      setupBasicAuthentication(builder);
    }

    return builder;
  }

  private void setupBasicAuthentication(final HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            configuration.authentication.username, configuration.authentication.password));

    builder.setDefaultCredentialsProvider(credentialsProvider);
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
}
