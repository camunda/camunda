package io.camunda.zeebe.exporter.operate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.connect.CustomInstantDeserializer;
import io.camunda.operate.connect.CustomOffsetDateTimeDeserializer;
import io.camunda.operate.connect.CustomOffsetDateTimeSerializer;
import io.camunda.operate.property.ElasticsearchProperties;

public class NoSpringJacksonConfig {

  public static ObjectMapper buildObjectMapper() {

    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class,
        new CustomOffsetDateTimeSerializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(OffsetDateTime.class,
        new CustomOffsetDateTimeDeserializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(Instant.class, new CustomInstantDeserializer());

    return Jackson2ObjectMapperBuilder.json().modules(javaTimeModule, new Jdk8Module())
        .featuresToDisable(SerializationFeature.INDENT_OUTPUT,
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS)
        // make sure that Jackson uses setters and getters, not fields
        .visibility(PropertyAccessor.GETTER, Visibility.ANY)
        .visibility(PropertyAccessor.IS_GETTER, Visibility.ANY)
        .visibility(PropertyAccessor.SETTER, Visibility.ANY)
        .visibility(PropertyAccessor.FIELD, Visibility.NONE)
        .visibility(PropertyAccessor.CREATOR, Visibility.ANY).build();
  }

  public static DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(ElasticsearchProperties.DATE_FORMAT_DEFAULT);
  }

}
