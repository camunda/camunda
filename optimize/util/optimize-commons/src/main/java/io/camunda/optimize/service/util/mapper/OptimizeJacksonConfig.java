/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class OptimizeJacksonConfig {

  private final Object[] disabledFeatures = {
    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
    DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
    DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY,
    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
    SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS
  };

  private final Object[] enabledFeatures = {
    Feature.ALLOW_COMMENTS,
    DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
    MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
  };

  private final DateTimeFormatter optimizeDateTimeFormatter =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  @Bean("optimizeObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> optimizeObjectMapperCustomizer() {
    final JavaTimeModule javaTimeModule = constructTimeModule();

    final Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder =
        Jackson2ObjectMapperBuilder.json()
            .modules(new Jdk8Module(), javaTimeModule)
            .featuresToDisable(disabledFeatures)
            .featuresToEnable(enabledFeatures);

    final ObjectMapper mapper = jackson2ObjectMapperBuilder.build();

    final SimpleModule deseralizerModule = constructDeserializerModule(mapper);

    return builder ->
        builder
            .modulesToInstall(
                modules ->
                    modules.addAll(List.of(javaTimeModule, new Jdk8Module(), deseralizerModule)))
            .featuresToDisable(disabledFeatures)
            .featuresToEnable(enabledFeatures);
  }

  private JavaTimeModule constructTimeModule() {
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        OffsetDateTime.class, new CustomOffsetDateTimeSerializer(optimizeDateTimeFormatter));
    javaTimeModule.addSerializer(
        Date.class, new DateSerializer(false, new StdDateFormat().withColonInTimeZone(false)));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(optimizeDateTimeFormatter));
    return javaTimeModule;
  }

  public static SimpleModule constructDeserializerModule(final ObjectMapper mapper) {
    final SimpleModule deseralizerModule = new SimpleModule();
    deseralizerModule.addDeserializer(
        DefinitionOptimizeResponseDto.class, new CustomDefinitionDeserializer(mapper));
    deseralizerModule.addDeserializer(
        ReportDefinitionDto.class, new CustomReportDefinitionDeserializer(mapper));
    deseralizerModule.addDeserializer(
        AuthorizedReportDefinitionResponseDto.class,
        new CustomAuthorizedReportDefinitionDeserializer(mapper));
    deseralizerModule.addDeserializer(
        CollectionEntity.class, new CustomCollectionEntityDeserializer(mapper));
    mapper.registerModule(deseralizerModule);
    return deseralizerModule;
  }

  @Bean("optimizeObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    optimizeObjectMapperCustomizer().accept(builder);
    return builder.build();
  }
}
