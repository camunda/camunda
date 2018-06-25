package org.camunda.operate.es;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


@Component
@Configuration
public class ElasticsearchConnector {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  private Settings getElasticSearchSettings() {
    return Settings.builder()
      .put("cluster.name", operateProperties.getElasticsearch().getClusterName())
      .build();
  }

  @Bean
  public TransportClient esClient() {
    logger.debug("Creating Elasticsearch connection...");
    TransportClient transportClient = null;
    try {
      transportClient = new PreBuiltTransportClient(getElasticSearchSettings()).addTransportAddress(
        new TransportAddress(InetAddress.getByName(operateProperties.getElasticsearch().getHost()), operateProperties.getElasticsearch().getPort()));
      if (!checkHealth(transportClient, true)) {
        logger.warn("Elasticsearch cluster [{}] is not accessible", operateProperties.getElasticsearch().getClusterName());
      } else {
        logger.debug("Elasticsearch connection was successfully created.");
      }
    } catch (UnknownHostException ex) {
      logger.error(String
          .format("Unable to connect to Elasticsearch [%s:%s]", operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort()),
        ex);
      //TODO OPE-36
    }
    return transportClient;
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

  @Bean
  public DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(operateProperties.getElasticsearch().getDateFormat());
  }

  @Bean("esObjectMapper")
  public ObjectMapper objectMapper() {

    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(dateTimeFormatter()));

    ObjectMapper result = Jackson2ObjectMapperBuilder.json().modules(javaTimeModule)
      .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
      .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT).build();
    return result;
  }

  public static class CustomSerializer extends JsonSerializer<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public CustomSerializer(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeString(value.format(this.formatter));
    }
  }

  public static class CustomDeserializer extends JsonDeserializer<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public CustomDeserializer(DateTimeFormatter formatter) {
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


}
