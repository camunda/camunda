package org.camunda.operate.schema.indices;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MigrationRepositoryIndex extends AbstractIndexDescriptor{

  public static final String INDEX_NAME = "migration-steps-repository";

  @Override
  public String getMainIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getIndexName() {
    return String.format("%s-%s", operateProperties.getElasticsearch().getIndexPrefix(), getMainIndexName());
  }

}
