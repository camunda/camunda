/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.apache.http.HttpHost;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;


@Component
@Configuration
public class ElasticsearchConnector {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Bean
  public RestHighLevelClient esClient() {
    //some weird error when ELS sets available processors number for Netty - see https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createEsClient(operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort());
  }

  @Bean("zeebeEsClient")
  public RestHighLevelClient zeebeEsClient() {
    //some weird error when ELS sets available processors number for Netty - see https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createEsClient(operateProperties.getZeebeElasticsearch().getHost(), operateProperties.getZeebeElasticsearch().getPort());
  }
  
  public static void closeEsClient(RestHighLevelClient esClient) {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        logger.error("Could not close esClient",e);
      }
    }
  }

  public RestHighLevelClient createEsClient(String host, int port) {
    logger.debug("Creating Elasticsearch connection...");
    RestHighLevelClient esClient;
    esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, "http")));
    if (!checkHealth(esClient, true)) {
      logger.warn("Elasticsearch cluster is not accessible");
    } else {
      logger.debug("Elasticsearch connection was successfully created.");
    }
    return esClient;
  }

  public boolean checkHealth(RestHighLevelClient esClient, boolean reconnect) {
    //TODO temporary solution
    int attempts = 0;
    boolean successfullyConnected = false;
    while (attempts == 0 || (reconnect && attempts < 10 && !successfullyConnected)) {
      try {
        final ClusterHealthResponse clusterHealthResponse = esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
        //TODO do we need this?
        successfullyConnected = clusterHealthResponse.getClusterName().equals(operateProperties.getElasticsearch().getClusterName());
      } catch (IOException ex) {
        logger.warn("Unable to connect to Elasticsearch cluster [{}]. Will try again...", operateProperties.getElasticsearch().getClusterName());
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      attempts++;
    }
    return successfullyConnected;
  }

  public boolean checkHealth(boolean reconnect) {
    return checkHealth(esClient(), reconnect);
  }

  public static class CustomOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public CustomOffsetDateTimeSerializer(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeString(value.format(this.formatter));
    }
  }

  public static class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public CustomOffsetDateTimeDeserializer(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {

      OffsetDateTime parsedDate;
      try {
        parsedDate = OffsetDateTime.parse(parser.getText(), this.formatter);
      } catch(DateTimeParseException exception) {
        //
        parsedDate = ZonedDateTime
          .parse(parser.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault()))
          .toOffsetDateTime();
      }
      return parsedDate;
    }
  }


  public static class CustomInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      return Instant.ofEpochMilli(Long.valueOf(parser.getText()));
    }
  }

//  public static class CustomLocalDateSerializer extends JsonSerializer<LocalDate> {
//
//    private DateTimeFormatter formatter;
//
//    public CustomLocalDateSerializer(DateTimeFormatter formatter) {
//      this.formatter = formatter;
//    }
//
//    @Override
//    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws IOException {
//      gen.writeString(value.format(this.formatter));
//    }
//  }
//
//  public static class CustomLocalDateDeserializer extends JsonDeserializer<LocalDate> {
//
//    private DateTimeFormatter formatter;
//
//    public CustomLocalDateDeserializer(DateTimeFormatter formatter) {
//      this.formatter = formatter;
//    }
//
//    @Override
//    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
//
//      LocalDate parsedDate;
//      try {
//        parsedDate = LocalDate.parse(parser.getText(), this.formatter);
//      } catch(DateTimeParseException exception) {
//        //
//        parsedDate = LocalDate
//          .parse(parser.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
//      }
//      return parsedDate;
//    }
//  }


}
