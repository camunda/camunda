package org.camunda.optimize.service.engine.importing.index;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;
import org.camunda.optimize.dto.optimize.importing.VersionedDefinitionImportInformation;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionStock {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected BeanHelper beanHelper;

  private List<DefinitionImportInformation> availableProcessDefinitions = new ArrayList<>();
  private OffsetDateTime timeToLoad = OffsetDateTime.now().minusMinutes(1);

  public List<DefinitionImportInformation> getAvailableProcessDefinitions(EngineContext engineContext) {
    if (haveToReload()) {
      availableProcessDefinitions = retrieveDefinitionsToImport(engineContext);
      if (!availableProcessDefinitions.isEmpty()) {
        timeToLoad = OffsetDateTime.now().plusMinutes(5);
      }
    }
    return availableProcessDefinitions
      .stream()
      .map(DefinitionImportInformation::copy)
      .collect(Collectors.toList());
  }

  private boolean haveToReload() {
    OffsetDateTime now = OffsetDateTime.now();
    return timeToLoad.isBefore(now) ;
  }

  private List<DefinitionImportInformation> retrieveDefinitionsToImport(EngineContext engineContext) {
    List<DefinitionImportInformation> processDefinitionsToImport =
        retrieveDefinitionsToImportFromConfigurationProvidedList();
    if (processDefinitionsToImport.isEmpty()) {
      processDefinitionsToImport = retrieveDefinitionToImportFromEngine(engineContext);
    }
    return processDefinitionsToImport;
  }

  private List<DefinitionImportInformation> retrieveDefinitionsToImportFromConfigurationProvidedList() {
    List<DefinitionImportInformation> processDefinitionsToImport = new ArrayList<>();
    List<String> configuredProcessDefinitionIds =
        configurationService.getProcessDefinitionIdsToImport();

    for (String configuredProcessDefinitionId : configuredProcessDefinitionIds) {
      DefinitionImportInformation definitionImportInformation =
          new DefinitionImportInformation();
      definitionImportInformation.setDefinitionBasedImportIndex(0);
      definitionImportInformation.setMaxEntityCount(0);
      definitionImportInformation.setProcessDefinitionId(configuredProcessDefinitionId);
      processDefinitionsToImport.add(definitionImportInformation);
    }
    return processDefinitionsToImport;
  }

  private List<DefinitionImportInformation> retrieveDefinitionToImportFromEngine(EngineContext engineContext) {
    int currentStart = 0;
    long maxPageSize = configurationService.getEngineImportProcessDefinitionMaxPageSize();
    List<ProcessDefinitionEngineDto> currentPage = null;
    ProcessDefinitionFetcher engineEntityFetcher = beanHelper.getInstance(ProcessDefinitionFetcher.class, engineContext);

    try {
      currentPage = engineEntityFetcher.fetchProcessDefinitions(
          currentStart,
          maxPageSize
      );
    } catch (Exception e) {
      logger.error("can't read initial PD list from the engine [{}]", engineContext.getEngineAlias(), e);
    }

    HashMap<String, TreeSet<VersionedDefinitionImportInformation>> versionSortedProcesses = new HashMap<>();
    while (currentPage != null && !currentPage.isEmpty()) {

      for (ProcessDefinitionEngineDto dto : currentPage) {
        VersionedDefinitionImportInformation definitionImportInformation =
            new VersionedDefinitionImportInformation();
        definitionImportInformation.setDefinitionBasedImportIndex(0);
        definitionImportInformation.setMaxEntityCount(0);
        definitionImportInformation.setProcessDefinitionId(dto.getId());
        definitionImportInformation.setVersion(dto.getVersion());

        if (!versionSortedProcesses.containsKey(dto.getKey())) {
          versionSortedProcesses.put(
              dto.getKey(),
              new TreeSet<>((o1, o2) -> Integer.compare(o2.getVersion(), o1.getVersion()))
          );
        }
        versionSortedProcesses.get(dto.getKey()).add(definitionImportInformation);
      }
      currentStart = currentStart + currentPage.size();
      currentPage = engineEntityFetcher.fetchProcessDefinitions(currentStart, maxPageSize);
    }
    List<DefinitionImportInformation> result = buildSortedOrder(versionSortedProcesses);
    // transform to unversioned definition import information. Otherwise we have later
    // problems to store the information to Elasticsearch.
    result = result
        .stream()
        .map(DefinitionImportInformation::copy)
        .collect(Collectors.toList());
    return result;
  }

  private List<DefinitionImportInformation> buildSortedOrder(HashMap<String, TreeSet<VersionedDefinitionImportInformation>> processDefinitionsToImport) {
    ArrayList<DefinitionImportInformation> result = new ArrayList<>();
    while (!processDefinitionsToImport.isEmpty()) {
      Iterator<Map.Entry<String, TreeSet<VersionedDefinitionImportInformation>>> iterator =
          processDefinitionsToImport.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<String,TreeSet<VersionedDefinitionImportInformation>> entry = iterator.next();
        if (!entry.getValue().isEmpty()) {
          result.add(entry.getValue().pollFirst());
        } else {
          iterator.remove();
        }
      }
    }
    return result;
  }

  public void reset() {
    this.timeToLoad = OffsetDateTime.now().minusMinutes(1);
  }
}
