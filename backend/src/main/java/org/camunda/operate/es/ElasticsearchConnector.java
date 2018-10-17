package org.camunda.operate.es;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
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
  public TransportClient esClient() {
    return createEsClient(operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort(),
      operateProperties.getElasticsearch().getClusterName());
  }

  @Bean("zeebeEsClient")
  public TransportClient zeebeEsClient() {
    return createEsClient(operateProperties.getZeebeElasticsearch().getHost(), operateProperties.getZeebeElasticsearch().getPort(),
      operateProperties.getZeebeElasticsearch().getClusterName());
  }

  public TransportClient createEsClient(String host, int port, String clusterName) {
    logger.debug("Creating Elasticsearch connection...");
    TransportClient transportClient = null;
    try {
      transportClient = new PreBuiltTransportClient(getElasticSearchSettings(clusterName)).addTransportAddress(
        new TransportAddress(InetAddress.getByName(host), port));
      if (!checkHealth(transportClient, true)) {
        logger.warn("Elasticsearch cluster [{}] is not accessible", clusterName);
      } else {
        logger.debug("Elasticsearch connection was successfully created.");
      }
    } catch (UnknownHostException ex) {
      logger.error(String
          .format("Unable to connect to Elasticsearch [%s:%s]", host, port),
        ex);
      //TODO OPE-36
    }
    return transportClient;
  }

  private Settings getElasticSearchSettings(String clusterName) {
    return Settings.builder()
      .put("cluster.name", clusterName)
      .build();
  }

  public boolean checkHealth(TransportClient transportClient, boolean reconnect) {
    //TODO temporary solution
    int attempts = 0;
    boolean successfullyConnected = false;
    while (attempts == 0 || (reconnect && attempts < 10 && !successfullyConnected)) {
      try {
        final ClusterHealthResponse clusterHealthResponse = transportClient.admin().cluster().prepareHealth().get();
        successfullyConnected = clusterHealthResponse.getClusterName().equals(operateProperties.getElasticsearch().getClusterName());
      } catch (NoNodeAvailableException ex) {
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
