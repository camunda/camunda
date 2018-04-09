package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.util.BeanHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlFetcher
  extends RetryBackoffEngineEntityFetcher<ProcessDefinitionXmlEngineDto, IdSetBasedImportPage> {

  @Autowired
  private BeanHelper beanHelper;


  public ProcessDefinitionXmlFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected List<ProcessDefinitionXmlEngineDto> fetchEntities(IdSetBasedImportPage page) {
    Set<String> ids = page.getIds();
    return fetchAllXmls(new ArrayList<>(ids));
  }

  private List<ProcessDefinitionXmlEngineDto> fetchAllXmls(List<String> processDefinitionIds) {
    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>(processDefinitionIds.size());
    long requestStart = System.currentTimeMillis();
    for (String processDefinitionId : processDefinitionIds) {
      ProcessDefinitionXmlEngineDto xml = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(configurationService.getProcessDefinitionXmlEndpoint(processDefinitionId))
        .request(MediaType.APPLICATION_JSON)
        .get(ProcessDefinitionXmlEngineDto.class);
      xmls.add(xml);
    }
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] process definition xmls within [{}] ms",
      processDefinitionIds.size(),
      requestEnd - requestStart
    );

    return xmls;
  }
}
