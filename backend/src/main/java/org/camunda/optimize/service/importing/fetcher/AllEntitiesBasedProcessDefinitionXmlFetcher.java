package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AllEntitiesBasedProcessDefinitionXmlFetcher {

  private final Logger logger = LoggerFactory.getLogger(AllEntitiesBasedProcessDefinitionXmlFetcher.class);

  @Autowired
  private AllEntitiesSetBasedEngineEntityFetcher engineEntityFetcher;

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

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult, String engineAlias) {
    logger.debug("Using page size of [{}] for fetching [PD_XML].",
      pageSizeCalculator.getCalculatedPageSize());
    long startRequestTime = System.currentTimeMillis();
    List<ProcessDefinitionXmlEngineDto> list =  engineEntityFetcher.fetchProcessDefinitionXmls(
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
    return engineEntityFetcher.fetchProcessDefinitionCount(engineAlias);
  }

}
