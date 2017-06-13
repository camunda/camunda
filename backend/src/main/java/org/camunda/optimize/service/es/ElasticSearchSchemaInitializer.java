package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.type.DurationHeatmapTargetValueType;
import org.camunda.optimize.service.es.schema.type.EventType;
import org.camunda.optimize.service.es.schema.type.ImportIndexType;
import org.camunda.optimize.service.es.schema.type.DefinitionImportIndexType;
import org.camunda.optimize.service.es.schema.type.LicenseType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.UsersType;
import org.camunda.optimize.service.es.schema.type.VariableType;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class ElasticSearchSchemaInitializer {

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
  private DefinitionImportIndexType definitionImportIndexType;

  @PostConstruct
  public void initializeSchema() {
    initializeMappings();
    if (!schemaManager.schemaAlreadyExists()) {
      schemaManager.createOptimizeIndex();
    }
    schemaManager.updateMappings();
  }

  private void initializeMappings() {
    schemaManager.addMapping(eventType);
    schemaManager.addMapping(variableType);
    schemaManager.addMapping(processDefinitionType);
    schemaManager.addMapping(processDefinitionXmlType);
    schemaManager.addMapping(usersType);
    schemaManager.addMapping(importIndexType);
    schemaManager.addMapping(targetValueType);
    schemaManager.addMapping(processInstanceType);
    schemaManager.addMapping(definitionImportIndexType);
    schemaManager.addMapping(licenseType);
  }

}
