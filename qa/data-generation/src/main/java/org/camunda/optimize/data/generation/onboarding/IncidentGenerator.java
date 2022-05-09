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

  private static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "onboarding-data" +
    "/customer_onboarding_process_instances.json";
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
            customerOnboardingProcessInstances, StandardCharsets.UTF_8
          );
          if (customerOnboardingProcessInstancesAsString != null) {
            String[] processInstances = customerOnboardingProcessInstancesAsString.split("\\r?\\n");
            List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
            for (String processInstance : processInstances) {
              ElasticDumpEntryDto elasticDumpEntryDto = OBJECT_MAPPER.readValue(
                processInstance, ElasticDumpEntryDto.class
              );
              ProcessInstanceDto rawProcessInstanceFromDump = elasticDumpEntryDto.getProcessInstanceDto();
              if (rawProcessInstanceFromDump != null) {
                processInstanceDtos.add(elasticDumpEntryDto.getProcessInstanceDto());
              }
            }
            addIncidentsToProcessInstances(processInstanceDtos);
          } else {
            log.error(
              "Could not load customer onboarding process instances. Please validate the process instance json file.");
          }
        } else {
          log.error("Process instance file cannot be null.");
        }
      }
    } catch (IOException e) {
      log.error("Could not load process instances.", e);
    }
  }

  private static void addIncidentsToProcessInstances(final List<ProcessInstanceDto> processInstanceDtos) {
    final int totalProcessInstanceDto = processInstanceDtos.size();
    final int amountOfProcessesToModify = (totalProcessInstanceDto * 10) / 100;

    Collections.shuffle(processInstanceDtos);

    for (int i = 0; i < amountOfProcessesToModify; i++) {
      final ProcessInstanceDto processInstanceToModify = processInstanceDtos.get(i);
      final Optional<FlowNodeInstanceDto> optionalFirstServiceTask = getFirstServiceTask(
        processInstanceToModify.getFlowNodeInstances()
      );
      if (optionalFirstServiceTask.isPresent()) {
        final FlowNodeInstanceDto firstServiceTask = optionalFirstServiceTask.get();

        // calculate a random incident duration based on the service task start date
        final OffsetDateTime incidentStartDate = firstServiceTask.getStartDate();
        final long incidentEndDateTimestampInMs = ThreadLocalRandom.current().nextLong(
          incidentStartDate.toInstant().toEpochMilli(), incidentStartDate.plusDays(2).toInstant().toEpochMilli()
        );
        final long incidentDurationInMs = incidentEndDateTimestampInMs - incidentStartDate.toInstant().toEpochMilli();
        final long incidentDurationInSeconds = TimeUnit.MILLISECONDS.toSeconds(incidentDurationInMs);

        // inject an incident that started at the original service task start date
        final IncidentDto incidentDto = new IncidentDto();
        incidentDto.setId(UUID.randomUUID().toString());
        incidentDto.setDefinitionKey(processInstanceToModify.getProcessDefinitionKey());
        incidentDto.setProcessInstanceId(processInstanceToModify.getProcessInstanceId());
        incidentDto.setDefinitionVersion(processInstanceToModify.getProcessDefinitionVersion());
        incidentDto.setIncidentStatus(IncidentStatus.RESOLVED);
        incidentDto.setCreateTime(incidentStartDate);
        incidentDto.setDurationInMs(incidentDurationInMs);
        incidentDto.setEndTime(firstServiceTask.getStartDate().plusSeconds(incidentDurationInSeconds));
        incidentDto.setActivityId(firstServiceTask.getFlowNodeId());
        processInstanceToModify.setIncidents(List.of(incidentDto));

        // now shift all flow node instances that started after the incident by the duration of the incident
        processInstanceToModify.getFlowNodeInstances().stream()
          .filter(flowNodeInstanceDto ->
                    flowNodeInstanceDto.getStartDate().isAfter(incidentStartDate)
                      || flowNodeInstanceDto.getStartDate().equals(incidentStartDate)
          )
          .forEach(flowNodeInstanceDto -> {
            flowNodeInstanceDto.setStartDate(
              flowNodeInstanceDto.getStartDate().plusSeconds(incidentDurationInSeconds)
            );
            if (flowNodeInstanceDto.getEndDate() != null) {
              flowNodeInstanceDto.setEndDate(flowNodeInstanceDto.getEndDate().plusSeconds(incidentDurationInSeconds));
            }
          });

        // finally shift the endDate of the instance if present
        if (processInstanceToModify.getEndDate() != null) {
          processInstanceToModify.setEndDate(
            processInstanceToModify.getEndDate().plusSeconds(incidentDurationInSeconds)
          );
        }
      }
      processInstanceDtos.set(i, processInstanceToModify);
    }
    try {
      OBJECT_MAPPER.writeValue(new File("customer_onboarding_process_instances.json"), processInstanceDtos);
    } catch (JsonProcessingException e) {
      log.error("The process instance list could not be mapped to json.");
    } catch (IOException e) {
      log.error("Could not write process instances to json file.");
    }
  }

  private static Optional<FlowNodeInstanceDto> getFirstServiceTask(final List<FlowNodeInstanceDto> flowNodeInstanceDtos) {
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
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
        SerializationFeature.INDENT_OUTPUT
      )
      .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS)
      .build();
  }
}
