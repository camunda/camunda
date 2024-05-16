/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.protobuf.Type;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.indices.TasklistWebSessionIndex;
import io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IndexSchemaValidatorIT extends TasklistIntegrationTest {

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;
  @Autowired private SchemaManager schemaManager;
  @Autowired private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @BeforeEach
  public void createDefault() {
    elasticsearchSchemaManager.createDefaults();
  }

  @BeforeEach
  public void deleteIndices() {
    retryElasticsearchClient.deleteIndicesFor(schemaManager.getIndexPrefix()+"*");
  }



  @Test
  public void shouldValidateDynamicIndexWithAddedProperty(){

    //schemaManager.createSchema();
    //Create a new Schema on ElasticSearch
    //schemaManager.createIndex(indexDescriptor);
    final String indexName=schemaManager.getIndexPrefix()+"-"+TasklistWebSessionIndex.INDEX_NAME+"-"+TasklistWebSessionIndex.INDEX_VERSION+"_";
    System.out.println("IndexName: "+indexName);
    final Map<String, Object> document = Map.of("name", "test");

    final boolean created2 = retryElasticsearchClient.createOrUpdateDocument(indexName, "id", document);
    System.out.println("Created2: "+created2);
    final var diff = indexSchemaValidator.validateIndexMappings();
    System.out.println(diff);

    //Create a new Index with a new property
    //Validate the schema
    //Assert that the schema is valid



  }


  private String idxName(final String name) {
    return schemaManager.getIndexPrefix() + "-" + name;
  }
}
