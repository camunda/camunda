package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class AllEntitiesBasedProcessDefinitionFetcher  {

  private final Logger logger = LoggerFactory.getLogger(AllEntitiesBasedProcessDefinitionFetcher.class);

  @Autowired
  private DefinitionBasedEngineEntityFetcher definitionBasedEngineEntityFetcher;
  @Autowired
  private EngineEntityFetcherImpl engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;

  private PageSizeCalculator pageSizeCalculator;

  @PostConstruct
  private void init() {
    pageSizeCalculator = new PageSizeCalculator(
      configurationService.getEngineReadTimeout(),
      configurationService.getEngineImportProcessDefinitionMaxPageSize(),
      configurationService.getEngineImportProcessDefinitionMinPageSize()
    );
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult, String engineAlias) {
    logger.info("Using page size [{}] for fetching process definitions.", pageSizeCalculator.getCalculatedPageSize());
    long startRequestTime = System.currentTimeMillis();
    List<ProcessDefinitionEngineDto> list =  engineEntityFetcher.fetchProcessDefinitions(
      indexOfFirstResult,
      pageSizeCalculator.getCalculatedPageSize(),
      engineAlias
    );
    long endRequestTime = System.currentTimeMillis();
    long requestDuration = endRequestTime - startRequestTime;
    pageSizeCalculator.calculateNewPageSize(requestDuration);
    return list;
  }

  public int fetchProcessDefinitionCount(String engineAlias) throws OptimizeException {
    return definitionBasedEngineEntityFetcher.fetchProcessDefinitionCount(engineAlias);
  }

}
