package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class TotalityBasedProcessDefinitionXmlFetcher {

  private final Logger logger = LoggerFactory.getLogger(TotalityBasedProcessDefinitionXmlFetcher.class);

  @Autowired
  private TotalityBasedEngineEntityFetcher engineEntityFetcher;

  @Autowired
  private ConfigurationService configurationService;

  private PageSizeCalculator pageSizeCalculator;

  @PostConstruct
  private void init() {
    pageSizeCalculator = new PageSizeCalculator(
      configurationService.getEngineReadTimeout(),
      configurationService.getEngineImportProcessDefinitionXmlMaxPageSize(),
      configurationService.getEngineImportProcessDefinitionXmlMinPageSize()
    );
  }

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult) {
    logger.info("Using page size of [{}] for fetching process definition xmls.",
      pageSizeCalculator.getCalculatedPageSize());
    long startRequestTime = System.currentTimeMillis();
    List<ProcessDefinitionXmlEngineDto> list =  engineEntityFetcher.fetchProcessDefinitionXmls(
      indexOfFirstResult,
      pageSizeCalculator.getCalculatedPageSize()
    );
    long endRequestTime = System.currentTimeMillis();
    long requestDuration = endRequestTime - startRequestTime;
    pageSizeCalculator.calculateNewPageSize(requestDuration);
    return list;
  }

  public int fetchProcessDefinitionCount() throws OptimizeException {
    return engineEntityFetcher.fetchProcessDefinitionCount();
  }

}
