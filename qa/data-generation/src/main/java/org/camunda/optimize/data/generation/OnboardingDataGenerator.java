/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

@Slf4j
public class OnboardingDataGenerator {

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
  private static final String PROCESS_INSTANCE_LOCATION = "onboarding-data/process-instance-data.json";

  private OnboardingDataGenerator() {
    final ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    OptimizeIndexNameService optimizeIndexNameService = new OptimizeIndexNameService(configurationService);
    ElasticsearchMetadataService elasticsearchMetadataService = new ElasticsearchMetadataService(OBJECT_MAPPER);
    elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      elasticsearchMetadataService,
      configurationService,
      optimizeIndexNameService
    );
    elasticsearchClient = new OptimizeElasticsearchClient(ElasticsearchHighLevelRestClientBuilder.build(configurationService), optimizeIndexNameService);
    elasticSearchSchemaManager.initializeSchema(elasticsearchClient);
  }

  public static void main(String[] args) {
    final OnboardingDataGenerator onboardingDataGenerator = new OnboardingDataGenerator();
    final Map<String, String> arguments = extractArguments(args);
    ProcessInstanceDto processInstanceDto = readProcessInstanceJson();
    if (processInstanceDto != null) {
      onboardingDataGenerator.addProcessInstanceCopiesToElasticSearch(Integer.parseInt(arguments.get(
        "numberOfProcessInstances")), processInstanceDto);
    } else {
      log.error("The given json file does not contain a process instance.");
    }
    onboardingDataGenerator.close();
  }

  private void addProcessInstanceCopiesToElasticSearch(final int amountOfProcessInstances,
                                                       final ProcessInstanceDto processInstanceDto) {
    BulkRequest bulkRequest = new BulkRequest();
    elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
      elasticsearchClient,
      new ProcessInstanceIndex(processInstanceDto.getProcessDefinitionKey()),
      Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS)
    );
    try {
      for (int counter = 0; counter < amountOfProcessInstances; counter++) {
        String processInstanceId = UUID.randomUUID().toString();
        processInstanceDto.setProcessInstanceId(processInstanceId);
        processInstanceDto.getFlowNodeInstances()
          .forEach(flowNodeInstanceDto -> flowNodeInstanceDto.setProcessInstanceId(processInstanceId));
        processInstanceDto.getIncidents()
          .forEach(incidentDto -> incidentDto.setProcessInstanceId(processInstanceId));
        String json = OBJECT_MAPPER.writeValueAsString(processInstanceDto);
        IndexRequest request = new IndexRequest(PROCESS_INSTANCE_INDEX_PREFIX + processInstanceDto.getProcessDefinitionKey())
            .id(processInstanceDto.getProcessInstanceId())
            .source(json, XContentType.JSON);
        bulkRequest.add(request);
      }
      elasticsearchClient.bulk(bulkRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Unable to add process instances to elasticsearch", e);
    }
  }

  private static ProcessInstanceDto readProcessInstanceJson() {
    ProcessInstanceDto processInstanceDto = null;
    try {
      ClassLoader classLoader = OnboardingDataGenerator.class.getClassLoader();
      File file = new File(classLoader.getResource(PROCESS_INSTANCE_LOCATION).getFile());
      processInstanceDto = OBJECT_MAPPER.readValue(file, ProcessInstanceDto.class);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Unable read process instances from json file.", e);
    }
    return processInstanceDto;
  }

  private static ObjectMapper createObjectMapper() {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));

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
        SerializationFeature.INDENT_OUTPUT
      )
      .build();
  }

  @SneakyThrows
  private void close() {
    elasticsearchClient.close();
  }

  private static Map<String, String> extractArguments(final String[] args) {
    final Map<String, String> arguments = new HashMap<>();
    fillArgumentMapWithDefaultValues(arguments);
    for (int i = 0; i < args.length; i++) {
      final String identifier = stripLeadingHyphens(args[i]);
      ensureIdentifierIsKnown(arguments, identifier);
      final String value = args.length > i + 1 ? args[i + 1] : null;
      if (!StringUtils.isBlank(value) && value.indexOf("--") != 0) {
        arguments.put(identifier, value);
        // increase i one further as we have a value argument here
        i += 1;
      }
    }
    return arguments;
  }

  private static void ensureIdentifierIsKnown(Map<String, String> arguments, String identifier) {
    if (!arguments.containsKey(identifier)) {
      throw new RuntimeException("Unknown argument [" + identifier + "]!");
    }
  }

  private static String stripLeadingHyphens(String str) {
    int index = str.lastIndexOf("-");
    if (index != -1) {
      return str.substring(index + 1);
    } else {
      return str;
    }
  }

  private static Map<String, String> fillArgumentMapWithDefaultValues(Map<String, String> arguments) {
    arguments.put("numberOfProcessInstances", String.valueOf(1000));
    return arguments;
  }
}
