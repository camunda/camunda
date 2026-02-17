/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.connect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.search.connect.es.builder.ElasticsearchClientBuilder;
import io.camunda.search.connect.es.builder.ElasticsearchHealthCheck;
import io.camunda.search.connect.es.builder.ProxyConfig;
import io.camunda.search.connect.es.builder.SslConfig;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.ElasticsearchProperties;
import io.camunda.tasklist.property.ProxyProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  private final PluginRepository esClientRepository = new PluginRepository();
  @Autowired private TasklistProperties tasklistProperties;

  @VisibleForTesting
  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  @Bean
  public ElasticsearchClient tasklistEsClient() {
    esClientRepository.load(tasklistProperties.getElasticsearch().getInterceptorPlugins());
    return createEsClient(tasklistProperties.getElasticsearch(), esClientRepository);
  }

  public ElasticsearchClient createEsClient(
      final ElasticsearchProperties elsConfig, final PluginRepository pluginRepository) {
    LOGGER.debug("Creating Elasticsearch connection...");

    final var client = configureBuilder(elsConfig, pluginRepository).build();

    if (tasklistProperties.getElasticsearch().isHealthCheckEnabled()) {
      if (!checkHealth(client)) {
        LOGGER.warn("Elasticsearch cluster is not accessible");
      } else {
        LOGGER.debug("Elasticsearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("Elasticsearch cluster health check is disabled.");
    }
    return client;
  }

  private ElasticsearchClientBuilder configureBuilder(
      final ElasticsearchProperties elsConfig, final PluginRepository pluginRepository) {
    final var builder =
        ElasticsearchClientBuilder.builder()
            .withObjectMapper(CommonUtils.OBJECT_MAPPER)
            .withBasicAuth(elsConfig.getUsername(), elsConfig.getPassword())
            .withConnectTimeout(elsConfig.getConnectTimeout())
            .withSocketTimeout(elsConfig.getSocketTimeout())
            .withRequestInterceptors(pluginRepository.asRequestInterceptor());

    // URLs
    final var urls = elsConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      builder.withUrls(urls);
    } else {
      builder.withUrls(List.of(elsConfig.getUrl()));
    }

    // SSL
    final SslProperties sslConfig = elsConfig.getSsl();
    if (sslConfig != null && sslConfig.getCertificatePath() != null) {
      builder.withSslConfig(toSslConfig(sslConfig));
    }

    // Proxy
    final ProxyProperties proxyConfig = elsConfig.getProxy();
    if (proxyConfig != null && proxyConfig.isEnabled()) {
      builder.withProxyConfig(
          ProxyConfig.builder()
              .host(proxyConfig.getHost())
              .port(proxyConfig.getPort())
              .sslEnabled(proxyConfig.isSslEnabled())
              .username(proxyConfig.getUsername())
              .password(proxyConfig.getPassword())
              .build());
    }

    return builder;
  }

  private SslConfig toSslConfig(final SslProperties sslConfig) {
    return SslConfig.builder()
        .enabled(true)
        .certificatePath(sslConfig.getCertificatePath())
        .selfSigned(sslConfig.isSelfSigned())
        .verifyHostname(sslConfig.isVerifyHostname())
        .build();
  }

  boolean checkHealth(final ElasticsearchClient esClient) {
    final ElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    return ElasticsearchHealthCheck.builder()
        .client(esClient)
        .expectedClusterName(elsConfig.getClusterName())
        .build()
        .check();
  }

  public static class CustomOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    private final DateTimeFormatter formatter;

    public CustomOffsetDateTimeSerializer(final DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public void serialize(
        final OffsetDateTime value, final JsonGenerator gen, final SerializerProvider provider)
        throws IOException {
      if (value == null) {
        gen.writeNull();
      } else {
        gen.writeString(value.format(formatter));
      }
    }
  }

  public static class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private final DateTimeFormatter formatter;

    public CustomOffsetDateTimeDeserializer(final DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public OffsetDateTime deserialize(final JsonParser parser, final DeserializationContext context)
        throws IOException {

      final OffsetDateTime parsedDate;
      try {
        parsedDate = OffsetDateTime.parse(parser.getText(), formatter);
      } catch (final DateTimeParseException exception) {
        throw new TasklistRuntimeException(
            "Exception occurred when deserializing date.", exception);
      }
      return parsedDate;
    }
  }

  public static class CustomInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(final JsonParser parser, final DeserializationContext context)
        throws IOException {
      return Instant.ofEpochMilli(Long.parseLong(parser.getText()));
    }
  }
}
