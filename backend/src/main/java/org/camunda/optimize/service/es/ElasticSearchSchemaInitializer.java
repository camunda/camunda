package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType;
import org.camunda.optimize.service.es.schema.type.EventType;
import org.camunda.optimize.service.es.schema.type.LicenseType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.es.schema.type.FinishedProcessInstanceIdTrackingType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.ReportType;
import org.camunda.optimize.service.es.schema.type.UnfinishedProcessInstanceIdTrackingType;
import org.camunda.optimize.service.es.schema.type.UnfinishedProcessInstanceTrackingType;
import org.camunda.optimize.service.es.schema.type.UsersType;
import org.camunda.optimize.service.es.schema.type.VariableProcessInstanceTrackingType;
import org.camunda.optimize.service.es.schema.type.VariableType;
import org.camunda.optimize.service.es.schema.type.index.DefinitionImportIndexType;
import org.camunda.optimize.service.es.schema.type.index.ImportIndexType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ElasticSearchSchemaInitializer {
  private boolean initialized = false;
  private final Logger logger = LoggerFactory.getLogger(ElasticSearchSchemaInitializer.class);
  @Autowired
  private ElasticSearchSchemaManager schemaManager;

  @Autowired
  private EventType eventType;

  @Autowired
  private VariableType variableType;

  @Autowired
  private DurationHeatmapTargetValueType targetValueType;

  @Autowired
  private ProcessDefinitionType processDefinitionType;

  @Autowired
  private ProcessDefinitionXmlType processDefinitionXmlType;

  @Autowired
  private UsersType usersType;

  @Autowired
  private LicenseType licenseType;

  @Autowired
  private ProcessInstanceType processInstanceType;

  @Autowired
  private ImportIndexType importIndexType;

  @Autowired
  private FinishedProcessInstanceIdTrackingType finishedProcessInstanceIdTrackingType;

  @Autowired
  private UnfinishedProcessInstanceIdTrackingType unfinishedProcessInstanceIdTrackingType;

  @Autowired
  private UnfinishedProcessInstanceTrackingType unfinishedProcessInstanceTrackingType;

  @Autowired
  private VariableProcessInstanceTrackingType variableProcessInstanceTrackingType;

  @Autowired
  private DefinitionImportIndexType definitionImportIndexType;

  @Autowired
  private ReportType reportType;

  @Autowired
  private DashboardType dashboardType;

  public void initializeSchema() {
    if (!initialized) {
      try {
        if (!schemaManager.schemaAlreadyExists()) {
          schemaManager.createOptimizeIndex();
        }
        schemaManager.updateMappings();
        initialized = true;
      } catch (NoNodeAvailableException e) {
        logger.error("can't handle schema initialization\\update", e);
      }

    }
  }

  @PostConstruct
  public void initializeMappings() {
    schemaManager.addMapping(eventType);
    schemaManager.addMapping(finishedProcessInstanceIdTrackingType);
    schemaManager.addMapping(unfinishedProcessInstanceTrackingType);
    schemaManager.addMapping(unfinishedProcessInstanceIdTrackingType);
    schemaManager.addMapping(variableProcessInstanceTrackingType);
    schemaManager.addMapping(variableType);
    schemaManager.addMapping(processDefinitionType);
    schemaManager.addMapping(processDefinitionXmlType);
    schemaManager.addMapping(usersType);
    schemaManager.addMapping(importIndexType);
    schemaManager.addMapping(targetValueType);
    schemaManager.addMapping(processInstanceType);
    schemaManager.addMapping(definitionImportIndexType);
    schemaManager.addMapping(licenseType);
    schemaManager.addMapping(reportType);
    schemaManager.addMapping(dashboardType);
  }

  /**
   * This method has to be invoked before schema initialization can be triggered
   */
  public void useClient(Client instance, ConfigurationService configurationService) {
    schemaManager.setEsclient(instance);
    schemaManager.setConfigurationService(configurationService);
  }

  public boolean isInitialized() {
    return initialized;
  }

  public void setInitialized(boolean initialized) {
    this.initialized = initialized;
  }
}
