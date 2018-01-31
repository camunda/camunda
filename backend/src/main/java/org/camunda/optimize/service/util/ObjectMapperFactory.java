package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Askar Akhmerov
 */
@Component
public class ObjectMapperFactory {
  private ObjectMapper result;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private ConfigurationService configurationService;

  /**
   * please not that instantiation is delegated to spring
   * @return
   */
  public ObjectMapper createOptimizeMapper() {
    if (result == null) {

      result = buildObjectMapper(dateTimeFormatter);
    }
    return result;
  }

  private ObjectMapper buildObjectMapper(DateTimeFormatter deserializationDateTimeFormatter) {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(this.dateTimeFormatter));
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

  public ObjectMapper createEngineMapper() {
    DateTimeFormatter engineDateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
    return buildObjectMapper(engineDateTimeFormatter);
  }
}
