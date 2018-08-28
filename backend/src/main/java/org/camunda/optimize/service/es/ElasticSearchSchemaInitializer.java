package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.type.AlertType;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.es.schema.type.DashboardShareType;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType;
import org.camunda.optimize.service.es.schema.type.LicenseType;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.ReportShareType;
import org.camunda.optimize.service.es.schema.type.SingleReportType;
import org.camunda.optimize.service.es.schema.type.index.ImportIndexType;
import org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType;
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
  private DurationHeatmapTargetValueType targetValueType;

  @Autowired
  private ProcessDefinitionType processDefinitionType;

  @Autowired
  private LicenseType licenseType;

  @Autowired
  private ProcessInstanceType processInstanceType;

  @Autowired
  private ImportIndexType importIndexType;

  @Autowired
  private TimestampBasedImportIndexType timestampBasedImportIndexType;

  @Autowired
  private SingleReportType singleReportType;

  @Autowired
  private CombinedReportType combinedReportType;

  @Autowired
  private DashboardType dashboardType;

  @Autowired
  private AlertType alertType;

  @Autowired
  private ReportShareType reportShareType;

  @Autowired
  private DashboardShareType dashboardShareType;

  @Autowired
  private MetadataType metadataType;

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
    schemaManager.addMapping(processDefinitionType);
    schemaManager.addMapping(importIndexType);
    schemaManager.addMapping(targetValueType);
    schemaManager.addMapping(processInstanceType);
    schemaManager.addMapping(timestampBasedImportIndexType);
    schemaManager.addMapping(licenseType);
    schemaManager.addMapping(singleReportType);
    schemaManager.addMapping(combinedReportType);
    schemaManager.addMapping(dashboardType);
    schemaManager.addMapping(alertType);
    schemaManager.addMapping(reportShareType);
    schemaManager.addMapping(dashboardShareType);
    schemaManager.addMapping(metadataType);
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
