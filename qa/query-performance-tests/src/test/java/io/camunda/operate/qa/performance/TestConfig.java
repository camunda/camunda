/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.performance;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import io.camunda.operate.es.ElasticsearchConnector;
import io.camunda.operate.property.ElasticsearchProperties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@ComponentScan(basePackages = {
    "io.camunda.operate.util.rest","io.camunda.operate.property",
    "io.camunda.operate.schema.indices",
    "io.camunda.operate.schema.templates",
    "io.camunda.operate.qa.performance"})
@EnableConfigurationProperties
public class TestConfig {

  @Bean
  public DateTimeFormatter getDateTimeFormatter() {
    return DateTimeFormatter.ofPattern(ElasticsearchProperties.DATE_FORMAT_DEFAULT);
  }

  @Bean
  public PropertySourcesPlaceholderConfigurer properties() {
    PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    propertySourcesPlaceholderConfigurer.setProperties(yaml.getObject());
    return propertySourcesPlaceholderConfigurer;
  }

  @Bean
  public ObjectMapper getObjectMapper() {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new ElasticsearchConnector.CustomOffsetDateTimeSerializer(getDateTimeFormatter()));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new ElasticsearchConnector.CustomOffsetDateTimeDeserializer(getDateTimeFormatter()));
    return Jackson2ObjectMapperBuilder.json().modules(javaTimeModule, new Jdk8Module())
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT).build();
  }

}
