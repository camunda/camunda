package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.service.engine.importing.index.page.ImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import java.util.List;

@Component
public abstract class EngineEntityFetcher<ENG extends EngineDto, PAGE extends ImportPage> {

  public static final String UTF8 = "UTF-8";
  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected Client client;
  @Autowired
  protected ConfigurationService configurationService;

  protected String engineAlias;

  public EngineEntityFetcher(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  /**
   * Queries the engine to fetch the entities from there given a page,
   * which contains all the information of which chunk of data should be fetched.
   */
  public abstract List<ENG> fetchEngineEntities(PAGE page);

  protected String getEngineAlias() {
    return engineAlias;
  }

}
