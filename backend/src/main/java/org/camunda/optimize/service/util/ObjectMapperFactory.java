package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;


@Component
public class ObjectMapperFactory {
  private DateTimeFormatter optimizeDateTimeFormatter;
  private DateTimeFormatter engineDateTimeFormatter;

  @Autowired
  public ObjectMapperFactory(final DateTimeFormatter optimizeDateTimeFormatter,
                             final ConfigurationService configurationService) {
    this.optimizeDateTimeFormatter = optimizeDateTimeFormatter;
    this.engineDateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  @Primary
  @Qualifier("optimizeMapper")
  @Bean
  public ObjectMapper createOptimizeMapper() {
    return buildObjectMapper(optimizeDateTimeFormatter);
  }

  @Qualifier("engineMapper")
  @Bean
  public ObjectMapper createEngineMapper() {
    return buildObjectMapper(engineDateTimeFormatter);
  }

  public ObjectMapper buildObjectMapper(DateTimeFormatter deserializationDateTimeFormatter) {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(this.optimizeDateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(deserializationDateTimeFormatter));

    return Jackson2ObjectMapperBuilder
      .json()
      .modules(javaTimeModule)
      .featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
      )
      .featuresToEnable(
        JsonParser.Feature.ALLOW_COMMENTS,
        SerializationFeature.INDENT_OUTPUT,
        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY
      )
      .build();
  }
}
