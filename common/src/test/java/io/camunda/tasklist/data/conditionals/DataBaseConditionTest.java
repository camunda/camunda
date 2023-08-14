/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.data.conditionals;

import static io.camunda.tasklist.data.conditionals.DataBaseCondition.DATABASE_PROPERTY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

class DataBaseConditionTest {

  private static Stream<Arguments> databaseConditionTestData() {
    return Stream.of(
        Arguments.of(new ElasticSearchCondition(), "elasticsearch", true),
        Arguments.of(new ElasticSearchCondition(), "ElasticSearch", true),
        Arguments.of(new ElasticSearchCondition(), "opensearch", false),
        Arguments.of(new ElasticSearchCondition(), null, true),
        Arguments.of(new ElasticSearchCondition(), "", true),
        Arguments.of(new OpenSearchCondition(), "opensearch", true),
        Arguments.of(new OpenSearchCondition(), "OpenSearch", true),
        Arguments.of(new OpenSearchCondition(), "elasticsearch", false),
        Arguments.of(new OpenSearchCondition(), null, false),
        Arguments.of(new OpenSearchCondition(), "", false));
  }

  @ParameterizedTest
  @MethodSource("databaseConditionTestData")
  void matches(DataBaseCondition condition, String databaseName, boolean expected) {
    // given
    final var context = mock(ConditionContext.class);
    final var env = mock(Environment.class);
    when(context.getEnvironment()).thenReturn(env);
    when(env.getProperty(DATABASE_PROPERTY)).thenReturn(databaseName);

    // when
    final boolean result = condition.matches(context, mock(AnnotatedTypeMetadata.class));

    // then
    assertThat(result).isEqualTo(expected);
  }
}
