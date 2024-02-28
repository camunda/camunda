/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch.client.sync;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import io.camunda.operate.schema.indices.UserIndex;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OpenSearchDocumentOperationsIT extends AbstractOpenSearchOperationIT {
  @Autowired private UserIndex userIndex;

  @Test
  public void searchUniqueShouldDeserializeLocalRecord() {
    // given
    String id = "id";
    opensearchTestDataHelper.addUser(id, "displayName", "password");

    // when
    record Result(String displayName) {}
    var searchRequestBuilder =
        searchRequestBuilder(userIndex.getFullQualifiedName())
            .query(term("userId", id))
            .source(sourceInclude("displayName"));

    final Result result =
        richOpenSearchClient.doc().searchUnique(searchRequestBuilder, Result.class, id);

    // then
    assertThat(result.displayName()).isEqualTo("displayName");
  }
}
