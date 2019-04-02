/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.exporter;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.protocol.clientapi.ValueType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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

  private final ElasticsearchExporterConfiguration configuration;
  private final Logger log;
  protected final RestHighLevelClient client;
  private BulkRequest bulkRequest;

  private final DateTimeFormatter formatter;

  public ElasticsearchClient(final ElasticsearchExporterConfiguration configuration, Logger log) {
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
    final IndexRequest request =
        new IndexRequest(indexFor(record), typeFor(record), idFor(record))
            .source(record.toJson(), XContentType.JSON);
    bulk(request);
  }

  public void bulk(final IndexRequest indexRequest) {
    bulkRequest.add(indexRequest);
  }

  /** @return true if all bulk records where flushed successfully */
  public boolean flush() {
    boolean success = true;
    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse responses = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        success = checkBulkResponses(responses);
      } catch (IOException e) {
        throw new ElasticsearchExporterException("Failed to flush bulk", e);
      }

      if (success) {
        // all records where flushed, create new bulk request, otherwise retry next time
        bulkRequest = new BulkRequest();
      }
    }

    return success;
  }

  private boolean checkBulkResponses(final BulkResponse responses) {
    for (BulkItemResponse response : responses) {
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
    final String filename = indexTemplateForValueType(valueType);
    return putIndexTemplate(templateName, filename, INDEX_DELIMITER);
  }

  /** @return true if request was acknowledged */
  public boolean putIndexTemplate(
      final String templateName, final String filename, final String indexDelimiter) {
    final Map<String, Object> template;
    try (InputStream inputStream = ElasticsearchExporter.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        template = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new ElasticsearchExporterException(
            "Failed to find index template in classpath " + filename);
      }
    } catch (IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load index template from classpath " + filename, e);
    }

    // update prefix in template in case it was changed in configuration
    template.put("index_patterns", Collections.singletonList(templateName + indexDelimiter + "*"));

    // update alias in template in case it was changed in configuration
    template.put("aliases", Collections.singletonMap(templateName, Collections.EMPTY_MAP));

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
    } catch (IOException e) {
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

  private HttpAsyncClientBuilder setHttpClientConfigCallback(HttpAsyncClientBuilder builder) {
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (configuration.authentication.isPresent()) {
      setupBasicAuthentication(builder);
    }

    return builder;
  }

  private void setupBasicAuthentication(HttpAsyncClientBuilder builder) {
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
    } catch (URISyntaxException e) {
      throw new ElasticsearchExporterException("Failed to parse url " + url, e);
    }

    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  protected String indexFor(final Record<?> record) {
    return indexPrefixForValueType(record.getMetadata().getValueType())
        + INDEX_DELIMITER
        + formatter.format(record.getTimestamp());
  }

  protected String idFor(final Record<?> record) {
    return record.getMetadata().getPartitionId() + "-" + record.getPosition();
  }

  protected String typeFor(final Record<?> record) {
    return "_doc";
  }

  private String indexPrefixForValueType(final ValueType valueType) {
    return configuration.index.prefix + "-" + valueTypeToString(valueType);
  }

  private static String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replaceAll("_", "-");
  }

  private static String indexTemplateForValueType(final ValueType valueType) {
    return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
  }
}
