/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.camunda.optimize.service.db.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.db.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class CustomerOnboardingDataImportService {

  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final ProcessInstanceRepository processInstanceRepository;
  private final Environment environment;

  private static final String CUSTOMER_ONBOARDING_DEFINITION =
      "customer_onboarding_definition.json";
  private static final String PROCESSED_INSTANCES = "customer_onboarding_process_instances.json";
  private static final int BATCH_SIZE = 2000;

  @EventListener(ApplicationReadyEvent.class)
  public void importData() {
    importData(PROCESSED_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION, BATCH_SIZE);
  }

  public void importData(
      final String processInstances, final String processDefinition, final int batchSize) {
    if (configurationService.getCustomerOnboardingImport()) {
      importCustomerOnboardingData(
          processDefinition,
          processInstances,
          batchSize,
          ConfigurationService.getOptimizeProfile(environment));
    } else {
      log.info("C8 Customer onboarding data disabled, will not perform data import");
    }
  }

  private void importCustomerOnboardingData(
      final String processDefinition,
      final String pathToProcessInstances,
      final int batchSize,
      final OptimizeProfile optimizeProfile) {
    DataSourceDto dataSource;
    final boolean isC7mode = optimizeProfile.equals(OptimizeProfile.PLATFORM);
    if (isC7mode) {
      log.info(
          "C8 Customer onboarding data enabled but running in Platform mode. Converting data to C7 test data");
      dataSource = getC7DataSource();
    } else {
      // In C8 modes, the file is already generated with the "<default>" tenant and zeebe-record
      // data source so these values
      // don't need changing
      dataSource = null;
      log.info("C8 Customer onboarding data enabled, importing customer onboarding data");
    }

    try (InputStream customerOnboardingDefinition =
        this.getClass().getClassLoader().getResourceAsStream(processDefinition)) {
      if (customerOnboardingDefinition != null) {
        String result =
            new String(customerOnboardingDefinition.readAllBytes(), StandardCharsets.UTF_8);
        ProcessDefinitionOptimizeDto processDefinitionDto =
            objectMapper.readValue(result, ProcessDefinitionOptimizeDto.class);
        if (processDefinitionDto != null) {
          if (isC7mode) {
            processDefinitionDto.setDataSource(dataSource);
            processDefinitionDto.setTenantId(null);
          }
          Optional.ofNullable(processDefinitionDto.getKey())
              .ifPresentOrElse(
                  key -> {
                    processDefinitionWriter.importProcessDefinitions(List.of(processDefinitionDto));
                    readProcessInstanceJson(pathToProcessInstances, batchSize, dataSource);
                  },
                  () ->
                      log.error(
                          "Process definition data is invalid. Please check your json file."));
        } else {
          log.error(
              "Could not extract process definition from file in path: "
                  + CUSTOMER_ONBOARDING_DEFINITION);
        }
      } else {
        log.error("Process definition could not be loaded. Please validate your json file.");
      }
    } catch (IOException e) {
      log.error("Unable to add a process definition to database", e);
    }
    log.info("Customer onboarding data import complete");
  }

  private DataSourceDto getC7DataSource() {
    return configurationService.getConfiguredEngines().entrySet().stream()
        .findFirst()
        .map(engine -> new EngineDataSourceDto(engine.getKey()))
        .orElseThrow(
            () -> new OptimizeConfigurationException("No C7 engines configured as data source"));
  }

  private void readProcessInstanceJson(
      final String pathToProcessInstances, final int batchSize, final DataSourceDto dataSourceDto) {
    List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
    try {
      try (InputStream customerOnboardingProcessInstances =
          this.getClass().getClassLoader().getResourceAsStream(pathToProcessInstances)) {
        if (customerOnboardingProcessInstances != null) {
          String result =
              new String(customerOnboardingProcessInstances.readAllBytes(), StandardCharsets.UTF_8);
          List<ProcessInstanceDto> rawProcessInstanceDtos =
              objectMapper.readValue(result, new TypeReference<>() {});
          for (ProcessInstanceDto processInstance : rawProcessInstanceDtos) {
            if (processInstance != null) {
              Optional<Long> processInstanceDuration =
                  Optional.ofNullable(processInstance.getDuration());
              if (dataSourceDto != null) {
                processInstance.setDataSource(dataSourceDto);
                processInstance.setTenantId(null);
                processInstance
                    .getFlowNodeInstances()
                    .forEach(flowNodeInstanceDto -> flowNodeInstanceDto.setTenantId(null));
                processInstance.getIncidents().forEach(incident -> incident.setTenantId(null));
              }
              if (processInstance.getProcessDefinitionKey() != null
                  && (processInstanceDuration.isEmpty() || processInstanceDuration.get() >= 0)) {
                processInstanceDtos.add(processInstance);
              }
            } else {
              log.error("Process instance not loaded correctly. Please check your json file.");
            }
          }
          loadProcessInstancesToDatabase(processInstanceDtos, batchSize);
        } else {
          log.error(
              "Could not load Camunda Customer Onboarding Demo process instances to input stream. Please validate the process "
                  + "instance json file.");
        }
      }
    } catch (IOException e) {
      log.error("Could not parse Camunda Customer Onboarding Demo process instances file.", e);
    }
  }

  private void loadProcessInstancesToDatabase(
      final List<ProcessInstanceDto> rawProcessInstanceDtos, final int batchSize) {
    List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
    Optional<OffsetDateTime> maxOfEndAndStartDate =
        rawProcessInstanceDtos.stream()
            .flatMap(instance -> Stream.of(instance.getStartDate(), instance.getEndDate()))
            .filter(Objects::nonNull)
            .max(OffsetDateTime::compareTo);
    for (ProcessInstanceDto rawProcessInstance : rawProcessInstanceDtos) {
      if (maxOfEndAndStartDate.isPresent()) {
        ProcessInstanceDto processInstanceDto =
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
    List<ProcessInstanceDto> completedProcessInstances =
        processInstanceDtos.stream()
            .filter(processInstanceDto -> processInstanceDto.getEndDate() != null)
            .collect(Collectors.toList());
    List<ProcessInstanceDto> runningProcessInstances =
        processInstanceDtos.stream()
            .filter(processInstanceDto -> processInstanceDto.getEndDate() == null)
            .collect(Collectors.toList());
    List<ImportRequestDto> completedProcessInstanceImports =
        completedProcessInstanceWriter.generateProcessInstanceImports(completedProcessInstances);
    processInstanceRepository.bulkImport(
        "Completed process instances", completedProcessInstanceImports);
    List<ImportRequestDto> runningProcessInstanceImports =
        runningProcessInstanceWriter.generateProcessInstanceImports(runningProcessInstances);
    if (!runningProcessInstanceImports.isEmpty()) {
      processInstanceRepository.bulkImport(
          "Running process instances", runningProcessInstanceImports);
    }
  }

  private ProcessInstanceDto modifyProcessInstanceDates(
      final ProcessInstanceDto processInstanceDto, final OffsetDateTime maxOfEndAndStartDate) {
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    long offset = ChronoUnit.SECONDS.between(maxOfEndAndStartDate, now);
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
