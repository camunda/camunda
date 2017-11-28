package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.PROCESS_DEFINITION_ID_IN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableInstanceCountFetcher extends AbstractEngineAwareFetcher {


  public VariableInstanceCountFetcher(String engineAlias) {
    super(engineAlias);
  }

  public Long fetchTotalProcessInstanceCountIfVariablesAreAvailable() {
    long totalCount = 0;

    long totalVariableInstances = fetchTotalVariableInstanceCount();

    if (totalVariableInstances != 0L) {
      if (configurationService.areProcessDefinitionsToImportDefined()) {
        for (String id : configurationService.getProcessDefinitionIdsToImport()) {
          WebTarget baseRequest = getEngineClient()
              .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
              .path(configurationService.getHistoricProcessInstanceCountEndpoint());

          baseRequest = baseRequest.queryParam(PROCESS_DEFINITION_ID, id);

          CountDto newCount = baseRequest
              .request()
              .acceptEncoding(UTF8)
              .get(CountDto.class);
          totalCount += newCount.getCount();
        }
      } else {
        WebTarget baseRequest = getEngineClient()
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
            .path(configurationService.getHistoricProcessInstanceCountEndpoint());
        CountDto newCount = baseRequest
            .request()
            .acceptEncoding(UTF8)
            .get(CountDto.class);
        totalCount += newCount.getCount();
      }

      //TODO: we need a better approach here, this is a dirty hack workaround
      totalCount = Math.min(totalCount,totalVariableInstances);
    }




    return totalCount;
  }

  private long fetchTotalVariableInstanceCount() {
    long totalCount = 0;

    CountDto newCount = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .queryParam("deserializeValues", "false")
      .path(configurationService.getHistoricVariableInstanceCountEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get()
      .readEntity(CountDto.class);
    totalCount += newCount.getCount();

    return totalCount;
  }

}
