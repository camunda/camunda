package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.type.BranchAnalysisDataType;
import org.camunda.optimize.service.es.schema.type.EventType;
import org.camunda.optimize.service.es.schema.type.ImportIndexType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType;
import org.camunda.optimize.service.es.schema.type.UsersType;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class ElasticSearchSchemaInitializer {

  @Autowired
  private ElasticSearchSchemaManager schemaManager;

  @Autowired
  private EventType eventType;

  @Autowired
  private BranchAnalysisDataType branchAnalysisDataType;

  @Autowired
  private ProcessDefinitionType processDefinitionType;

  @Autowired
  private ProcessDefinitionXmlType processDefinitionXmlType;

  @Autowired
  private UsersType usersType;

  @Autowired
  private ImportIndexType importIndexType;

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
    schemaManager.addMapping(branchAnalysisDataType);
    schemaManager.addMapping(processDefinitionType);
    schemaManager.addMapping(processDefinitionXmlType);
    schemaManager.addMapping(usersType);
    schemaManager.addMapping(importIndexType);
  }

}
