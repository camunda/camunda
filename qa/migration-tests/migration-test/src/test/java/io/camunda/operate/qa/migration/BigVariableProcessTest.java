/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration;

import static io.camunda.operate.qa.util.VariablesUtil.VAR_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.property.ImportProperties;
import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.util.EntityReader;
import io.camunda.operate.qa.migration.v100.BigVariableDataGenerator;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.ThreadUtil;
import java.util.List;
import org.assertj.core.api.Condition;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BigVariableProcessTest extends AbstractMigrationTest {

  private String bpmnProcessId = BigVariableDataGenerator.PROCESS_BPMN_PROCESS_ID;

  @Autowired
  private EntityReader entityReader;

  @Test
  public void testBigVariablesHasPreviewAndFullValue() {
    assumeThatProcessIsUnderTest(bpmnProcessId);

    ThreadUtil.sleepFor(5_000);
    SearchRequest searchRequest = new SearchRequest(variableTemplate.getAlias());
    searchRequest.source()
        .query(termsQuery(VariableTemplate.NAME, bpmnProcessId + "_var0",
            bpmnProcessId + "_var1", bpmnProcessId + "_var2"));
    final List<VariableEntity> vars = entityReader
        .searchEntitiesFor(searchRequest, VariableEntity.class);

    assertThat(vars).hasSize(3);
    //then "value" contains truncated value
    Condition<String> suffix = new Condition<>(s -> s.contains(VAR_SUFFIX), "contains");
    Condition<String> length =
        new Condition<>(
            s -> s.length() == ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD, "length");
    Condition<String> lengthGt =
        new Condition<>(
            s -> s.length() > ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD, "length");
    assertThat(vars).extracting(VariableEntity::getValue).areNot(suffix).are(length);
    assertThat(vars).extracting(VariableEntity::getFullValue).are(suffix).are(lengthGt);
    assertThat(vars).extracting(VariableEntity::getIsPreview).containsOnly(true);
  }


}
