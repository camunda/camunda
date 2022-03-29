/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ElasticDumpEntryDto;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.DataGenerationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

@Component
@Slf4j
@AllArgsConstructor
public class CustomerOnboardingDataImportService {

  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private static final String CUSTOMER_ONBOARDING_DEFINITION = "customer_onboarding_definition.json";
  private static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "customer_onboarding_process_instances.json";
  private static final int BATCH_SIZE = 5000;

  @PostConstruct
  public void importData() {
    importData(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, BATCH_SIZE);
  }

  public void importData(final String processInstances, final int batchSize) {
    if (configurationService.getCustomerOnboardingImport()) {
      importCustomerOnboardingDefinition();
      addProcessInstanceCopiesToElasticSearch(processInstances, batchSize);
    }
  }

  private void importCustomerOnboardingDefinition() {
    try {
      ClassLoader classLoader = CustomerOnboardingDataImportService.class.getClassLoader();
      URL resource = classLoader.getResource(CUSTOMER_ONBOARDING_DEFINITION);
      if (resource != null) {
        File file = new File(resource.getFile());
        ProcessDefinitionOptimizeDto processDefinitionDto = objectMapper.readValue(
          file,
          ProcessDefinitionOptimizeDto.class
        );
        if (processDefinitionDto != null) {
          processDefinitionWriter.importProcessDefinitions(List.of(processDefinitionDto));
        } else {
          throw new DataGenerationException("Could not read process definition json file in path: " + CUSTOMER_ONBOARDING_DEFINITION);
        }
      } else {
        throw new DataGenerationException("The json file " + CUSTOMER_ONBOARDING_DEFINITION + " does not contain a " +
                                            "process definition.");
      }
    } catch (IOException e) {
      throw new DataGenerationException("Unable to add a process definition to elasticsearch", e);
    }
  }

  private void addProcessInstanceCopiesToElasticSearch(final String pathToProcessInstances, final int batchSize) {
    try {
      ClassLoader classLoader = CustomerOnboardingDataImportService.class.getClassLoader();
      URL resource = classLoader.getResource(pathToProcessInstances);
      if (resource != null) {
        File file = new File(resource.getFile());
        List<ProcessInstanceDto> processInstanceDtos = new ArrayList<>();
        try (Scanner scanner = new Scanner(file)) {
          while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            ElasticDumpEntryDto elasticDumpEntryDto = objectMapper.readValue(line, ElasticDumpEntryDto.class);
            ProcessInstanceDto processInstanceDto = elasticDumpEntryDto.getProcessInstanceDto();
            Optional<Long> processInstanceDuration = Optional.ofNullable(processInstanceDto.getDuration());
            if (processInstanceDto.getProcessDefinitionKey() != null) {
              if(processInstanceDuration.isEmpty() || processInstanceDuration.get() >= 0) {
                processInstanceDtos.add(elasticDumpEntryDto.getProcessInstanceDto());
              }
            }
            if (processInstanceDtos.size() % batchSize == 0) {
              addProcessInstancesToElasticSearch(processInstanceDtos);
              processInstanceDtos.clear();
            }
          }
          if (!processInstanceDtos.isEmpty()) {
            addProcessInstancesToElasticSearch(processInstanceDtos);
          }
        } catch (FileNotFoundException e) {
          throw new DataGenerationException("Unable to locate file containing process definition", e);
        }
      }
    } catch (IOException e) {
      throw new DataGenerationException("Unable to add a process definition to elasticsearch", e);
    }
  }

  private void addProcessInstancesToElasticSearch(List<ProcessInstanceDto> processInstanceDtos) {
    List<ProcessInstanceDto> completedProcessInstances = processInstanceDtos.stream()
      .filter(processInstanceDto -> processInstanceDto.getEndDate() != null)
      .collect(
        Collectors.toList());
    List<ProcessInstanceDto> runningProcessInstances = processInstanceDtos.stream()
      .filter(processInstanceDto -> processInstanceDto.getEndDate() == null)
      .collect(Collectors.toList());
    List<ImportRequestDto> completedProcessInstanceImports =
      completedProcessInstanceWriter.generateProcessInstanceImports(
        completedProcessInstances);
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Completed process instances",
      completedProcessInstanceImports,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
    List<ImportRequestDto> runningProcessInstanceImports = runningProcessInstanceWriter.generateProcessInstanceImports(
      runningProcessInstances);
    if(!runningProcessInstanceImports.isEmpty()) {
      ElasticsearchWriterUtil.executeImportRequestsAsBulk(
        "Completed process instances",
        runningProcessInstanceImports,
        configurationService.getSkipDataAfterNestedDocLimitReached()
      );
    }
  }

}
