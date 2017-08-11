package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class ActivityInstanceFetcher {

  private final Logger logger = LoggerFactory.getLogger(ActivityInstanceFetcher.class);

  @Autowired
  private DefinitionBasedEngineEntityFetcher engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;

  private PageSizeCalculator pageSizeCalculator;

  @PostConstruct
  private void init() {
    pageSizeCalculator = new PageSizeCalculator(
      configurationService.getEngineReadTimeout(),
      configurationService.getEngineImportActivityInstanceMaxPageSize(),
      configurationService.getEngineImportActivityInstanceMinPageSize()
    );
  }

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(int indexOfFirstResult,
                                                                                String processDefinitionId) {

    logger.info("Using page size of [{}] for fetching historic activity instances.", pageSizeCalculator.getCalculatedPageSize());
    long startRequestTime = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> list =  engineEntityFetcher.fetchHistoricActivityInstances(
      indexOfFirstResult,
      pageSizeCalculator.getCalculatedPageSize(),
      processDefinitionId
    );
    long endRequestTime = System.currentTimeMillis();
    long requestDuration = endRequestTime - startRequestTime;
    pageSizeCalculator.calculateNewPageSize(requestDuration);
    return list;
  }

  public int fetchHistoricActivityInstanceCount(List<String> processDefinitionIds) throws OptimizeException {
    return engineEntityFetcher.fetchHistoricActivityInstanceCount(processDefinitionIds);
  }

}
