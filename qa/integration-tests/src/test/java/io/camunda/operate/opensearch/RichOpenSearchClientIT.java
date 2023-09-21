/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch;

import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OpensearchOperateIntegrationTest;
import io.camunda.operate.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.opensearch._types.Refresh;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

public class RichOpenSearchClientIT extends OpensearchOperateIntegrationTest {
  @Autowired
  RichOpenSearchClient richOpenSearchClient;

  @Autowired
  SchemaManager schemaManager;

  @Autowired
  OperateProperties operateProperties;

  @Autowired
  private UserIndex userIndex;

  private String indexPrefix;

  @Before
  public void setUp(){
    indexPrefix = "test-batch-request-"+ TestUtil.createRandomString(5);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
    schemaManager.createSchema();
  }

  @After
  public void cleanUp() {
    schemaManager.deleteIndicesFor(indexPrefix +"*");
  }

  @Test
  public void shouldDeserializeLocalRecord() {
    // given
    record Result(String displayName){}
    String id = "id";

    var user = new UserEntity();
    user.setUserId(id);
    user.setDisplayName("displayName");
    user.setPassword("password");

    var indexRequestBuilder = indexRequestBuilder(userIndex.getFullQualifiedName())
      .id(id)
      .document(user)
      .refresh(Refresh.True);

    richOpenSearchClient.doc().index(indexRequestBuilder);

    // when
    var searchRequestBuilder = searchRequestBuilder(userIndex.getFullQualifiedName())
      .query(term("userId", id))
      .source(sourceInclude("displayName"));

    final Result result = richOpenSearchClient.doc().searchUnique(searchRequestBuilder, Result.class, id);

    // then
    assertThat(result.displayName()).isEqualTo("displayName");
  }
}
