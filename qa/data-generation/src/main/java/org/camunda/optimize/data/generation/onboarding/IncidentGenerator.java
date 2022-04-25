/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.onboarding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ElasticDumpEntryDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Slf4j
public class IncidentGenerator {

  private static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "onboarding-data/customer_onboarding_process_instances.json";
  private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

  public static void main(String[] args) {
    loadProcessInstances(CUSTOMER_ONBOARDING_PROCESS_INSTANCES);
  }

  private static void loadProcessInstances(final String pathToProcessInstances) {
    try {
      if (pathToProcessInstances != null) {
        InputStream customerOnboardingProcessInstances = IncidentGenerator.class.getClassLoader()
          .getResourceAsStream(pathToProcessInstances);
        if (customerOnboardingProcessInstances != null) {
          String customerOnboardingProcessInstancesAsString = IOUtils.toString(
            customerOnboardingProcessInstances,
            StandardCharsets.UTF_8
          );
          if (customerOnboardingProcessInstancesAsString != null) {
            String[] processInstances = customerOnboardingProcessInstancesAsString.split("\\r?\\n");
            List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
            for (String processInstance : processInstances) {
              ElasticDumpEntryDto elasticDumpEntryDto = OBJECT_MAPPER.readValue(
                processInstance,
                ElasticDumpEntryDto.class
              );
              ProcessInstanceDto rawProcessInstanceFromDump = elasticDumpEntryDto.getProcessInstanceDto();
              if (rawProcessInstanceFromDump != null) {
                Optional<Long> processInstanceDuration = Optional.ofNullable(rawProcessInstanceFromDump.getDuration());
                if (rawProcessInstanceFromDump.getProcessDefinitionKey() != null && (processInstanceDuration.isEmpty() || processInstanceDuration.get() >= 0)) {
                  processInstanceDtos.add(elasticDumpEntryDto.getProcessInstanceDto());
                } else {
                  log.error("Process instance not loaded correctly. Please check your json file.");
                }
              }
            }
            addIncidentsToProcessInstancesses(processInstanceDtos);
          } else {
            log.error(
              "Could not load customer onboarding process instances. Please validate the process instance json file.");
          }
        } else {
          log.error("Process instance file cannot be null.");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void addIncidentsToProcessInstancesses(List<ProcessInstanceDto> processInstanceDtos) {
    int totalProcessInstanceDto = processInstanceDtos.size();
    int amountOfProcessesToModify = (totalProcessInstanceDto * 10) / 100;

    Collections.shuffle(processInstanceDtos);
    for (int i = 0; i < amountOfProcessesToModify; i++) {
      final ProcessInstanceDto processInstanceToModify = processInstanceDtos.get(i);
      Optional<FlowNodeInstanceDto> firstServiceTask =
        getFirstServiceTask(processInstanceToModify.getFlowNodeInstances());
      if (firstServiceTask.isPresent()) {
        OffsetDateTime incidentStartDate = firstServiceTask.get().getStartDate().plusMinutes(30);
        OffsetDateTime incidentMaxDuration = incidentStartDate.plusDays(2);
        long randomGeneratedDate = ThreadLocalRandom.current()
          .nextLong(incidentStartDate.toInstant().toEpochMilli(), incidentMaxDuration.toInstant().toEpochMilli());
        long randomGeneratedDuration = randomGeneratedDate - incidentStartDate.toInstant().getEpochSecond();
        long randomGeneratedDurationInSeconds = TimeUnit.MILLISECONDS.toSeconds(randomGeneratedDuration);
        IncidentDto incidentDto = new IncidentDto();
        incidentDto.setId(UUID.randomUUID().toString());
        incidentDto.setDefinitionKey(processInstanceToModify.getProcessDefinitionKey());
        incidentDto.setProcessInstanceId(processInstanceToModify.getProcessInstanceId());
        incidentDto.setDefinitionVersion(processInstanceToModify.getProcessDefinitionVersion());
        incidentDto.setIncidentStatus(IncidentStatus.RESOLVED);
        incidentDto.setCreateTime(firstServiceTask.get().getStartDate());
        incidentDto.setDurationInMs(randomGeneratedDuration);
        incidentDto.setEndTime(firstServiceTask.get().getStartDate().plusSeconds(randomGeneratedDurationInSeconds));
        processInstanceToModify.setIncidents(List.of(incidentDto));
        Optional<OffsetDateTime> optionalEndDate = Optional.ofNullable(processInstanceToModify.getEndDate());
        if (optionalEndDate.isPresent()) {
          processInstanceToModify.setEndDate(processInstanceToModify.getEndDate()
                                               .plusSeconds(randomGeneratedDurationInSeconds));
        }
        processInstanceToModify.getFlowNodeInstances().forEach(flowNodeInstanceDto -> {
          if (!flowNodeInstanceDto.getFlowNodeInstanceId().equals(firstServiceTask.get().getFlowNodeInstanceId())) {
            flowNodeInstanceDto.setStartDate(flowNodeInstanceDto.getStartDate()
                                               .plusSeconds(randomGeneratedDurationInSeconds));
            Optional<OffsetDateTime> optionalFlowNodeEndDate = Optional.ofNullable(flowNodeInstanceDto.getEndDate());
            if (optionalFlowNodeEndDate.isPresent()) {
              flowNodeInstanceDto.setEndDate(flowNodeInstanceDto.getEndDate()
                                               .plusSeconds(randomGeneratedDurationInSeconds));
            }
          }
        });
      }
      processInstanceDtos.set(i, processInstanceToModify);
    }
    try {
      ObjectMapper objectMapper = createObjectMapper();
      objectMapper.writeValue(new File("customer_onboarding_process_instances.json"), processInstanceDtos);
    } catch (JsonProcessingException e) {
      log.error("The process instance list could not be mapped to json.");
    } catch (IOException e) {
      log.error("Could not write process instances to json file.");
    }
  }

  private static Optional<FlowNodeInstanceDto> getFirstServiceTask(List<FlowNodeInstanceDto> flowNodeInstanceDtos) {
    return flowNodeInstanceDtos.stream()
      .filter(flowNodeInstanceDto -> flowNodeInstanceDto.getFlowNodeType().equals("serviceTask"))
      .min(Comparator.comparing(FlowNodeInstanceDto::getStartDate));
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
}
