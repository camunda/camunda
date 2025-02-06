/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CustomerOnboardingDataImportService {

  private static final String CUSTOMER_ONBOARDING_DEFINITION =
      "customer_onboarding_definition.json";
  private static final String PROCESSED_INSTANCES = "customer_onboarding_process_instances.json";
  private static final int BATCH_SIZE = 2000;
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CustomerOnboardingDataImportService.class);
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final ProcessInstanceWriter processInstanceWriter;
  private final ProcessInstanceRepository processInstanceRepository;

  public CustomerOnboardingDataImportService(
      final ProcessDefinitionWriter processDefinitionWriter,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final ProcessInstanceRepository processInstanceRepository) {
    this.processDefinitionWriter = processDefinitionWriter;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    this.processInstanceWriter = processInstanceWriter;
    this.processInstanceRepository = processInstanceRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void importData() {
    importData(PROCESSED_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION, BATCH_SIZE);
  }

  public void importData(
      final String processInstances, final String processDefinition, final int batchSize) {
    if (configurationService.getCustomerOnboardingImport()) {
      LOG.info("C8 Customer onboarding data enabled, importing data");
      importCustomerOnboardingData(processDefinition, processInstances, batchSize);
    } else {
      LOG.info("C8 Customer onboarding data disabled, will not perform data import");
    }
  }

  private void importCustomerOnboardingData(
      final String processDefinition, final String pathToProcessInstances, final int batchSize) {
    try (final InputStream customerOnboardingDefinition =
        getClass().getClassLoader().getResourceAsStream(processDefinition)) {
      if (customerOnboardingDefinition != null) {
        final String result =
            new String(customerOnboardingDefinition.readAllBytes(), StandardCharsets.UTF_8);
        final ProcessDefinitionOptimizeDto processDefinitionDto =
            objectMapper.readValue(result, ProcessDefinitionOptimizeDto.class);
        if (processDefinitionDto != null) {
          Optional.ofNullable(processDefinitionDto.getKey())
              .ifPresentOrElse(
                  key -> {
                    processDefinitionWriter.importProcessDefinitions(List.of(processDefinitionDto));
                    readProcessInstanceJson(pathToProcessInstances, batchSize);
                  },
                  () ->
                      LOG.error(
                          "Process definition data is invalid. Please check your json file."));
        } else {
          LOG.error(
              "Could not extract process definition from file in path: "
                  + CUSTOMER_ONBOARDING_DEFINITION);
        }
      } else {
        LOG.error("Process definition could not be loaded. Please validate your json file.");
      }
    } catch (final IOException e) {
      LOG.error("Unable to add a process definition to the database", e);
    }
    LOG.info("Customer onboarding data import complete");
  }

  private void readProcessInstanceJson(final String pathToProcessInstances, final int batchSize) {
    final List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
    try {
      try (final InputStream customerOnboardingProcessInstances =
          getClass().getClassLoader().getResourceAsStream(pathToProcessInstances)) {
        if (customerOnboardingProcessInstances != null) {
          final String result =
              new String(customerOnboardingProcessInstances.readAllBytes(), StandardCharsets.UTF_8);
          final List<ProcessInstanceDto> rawProcessInstanceDtos =
              objectMapper.readValue(result, new TypeReference<>() {});
          for (final ProcessInstanceDto processInstance : rawProcessInstanceDtos) {
            if (processInstance != null) {
              final Optional<Long> processInstanceDuration =
                  Optional.ofNullable(processInstance.getDuration());
              if (processInstance.getProcessDefinitionKey() != null
                  && (processInstanceDuration.isEmpty() || processInstanceDuration.get() >= 0)) {
                processInstanceDtos.add(processInstance);
              }
            } else {
              LOG.error("Process instance not loaded correctly. Please check your json file.");
            }
          }
          loadProcessInstancesToDatabase(processInstanceDtos, batchSize);
        } else {
          LOG.error(
              "Could not load Camunda Customer Onboarding Demo process instances to input stream. Please validate the process "
                  + "instance json file.");
        }
      }
    } catch (final IOException e) {
      LOG.error("Could not parse Camunda Customer Onboarding Demo process instances file.", e);
    }
  }

  private void loadProcessInstancesToDatabase(
      final List<ProcessInstanceDto> rawProcessInstanceDtos, final int batchSize) {
    final List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
    final Optional<OffsetDateTime> maxOfEndAndStartDate =
        rawProcessInstanceDtos.stream()
            .flatMap(instance -> Stream.of(instance.getStartDate(), instance.getEndDate()))
            .filter(Objects::nonNull)
            .max(OffsetDateTime::compareTo);
    for (final ProcessInstanceDto rawProcessInstance : rawProcessInstanceDtos) {
      if (maxOfEndAndStartDate.isPresent()) {
        final ProcessInstanceDto processInstanceDto =
            modifyProcessInstanceDates(rawProcessInstance, maxOfEndAndStartDate.get());
        processInstanceDtos.add(processInstanceDto);
        if (processInstanceDtos.size() % batchSize == 0) {
          insertProcessInstancesToDatabase(processInstanceDtos);
          processInstanceDtos.clear();
        }
      }
    }
    if (!processInstanceDtos.isEmpty()) {
      insertProcessInstancesToDatabase(processInstanceDtos);
    }
  }

  private void insertProcessInstancesToDatabase(
      final List<ProcessInstanceDto> processInstanceDtos) {
    final List<ProcessInstanceDto> completedProcessInstances =
        processInstanceDtos.stream()
            .filter(processInstanceDto -> processInstanceDto.getEndDate() != null)
            .collect(Collectors.toList());
    final List<ProcessInstanceDto> runningProcessInstances =
        processInstanceDtos.stream()
            .filter(processInstanceDto -> processInstanceDto.getEndDate() == null)
            .collect(Collectors.toList());
    final List<ImportRequestDto> completedProcessInstanceImports =
        processInstanceWriter.generateCompletedProcessInstanceImports(completedProcessInstances);
    processInstanceRepository.bulkImport(
        "Completed process instances", completedProcessInstanceImports);
    final List<ImportRequestDto> runningProcessInstanceImports =
        processInstanceWriter.generateRunningProcessInstanceImports(runningProcessInstances);
    if (!runningProcessInstanceImports.isEmpty()) {
      processInstanceRepository.bulkImport(
          "Running process instances", runningProcessInstanceImports);
    }
  }

  private ProcessInstanceDto modifyProcessInstanceDates(
      final ProcessInstanceDto processInstanceDto, final OffsetDateTime maxOfEndAndStartDate) {
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final long offset = ChronoUnit.SECONDS.between(maxOfEndAndStartDate, now);
    Optional.ofNullable(processInstanceDto.getStartDate())
        .ifPresent(startDate -> processInstanceDto.setStartDate(startDate.plusSeconds(offset)));
    Optional.ofNullable(processInstanceDto.getEndDate())
        .ifPresent(endDate -> processInstanceDto.setEndDate(endDate.plusSeconds(offset)));

    processInstanceDto
        .getFlowNodeInstances()
        .forEach(
            flowNodeInstanceDto -> {
              Optional.ofNullable(flowNodeInstanceDto.getStartDate())
                  .ifPresent(
                      startDate -> flowNodeInstanceDto.setStartDate(startDate.plusSeconds(offset)));
              Optional.ofNullable(flowNodeInstanceDto.getEndDate())
                  .ifPresent(
                      endDate -> flowNodeInstanceDto.setEndDate(endDate.plusSeconds(offset)));
            });
    return processInstanceDto;
  }
}
