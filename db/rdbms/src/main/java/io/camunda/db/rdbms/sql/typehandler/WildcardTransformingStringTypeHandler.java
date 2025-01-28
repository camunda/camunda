/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;

public class WildcardTransformingStringTypeHandler extends StringTypeHandler {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final String parameter, final JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, transformElasticsearchToSql(parameter));
  }

  public static String transformElasticsearchToSql(final String elasticsearchQuery) {
    // Regular expression to match unescaped wildcards
    final var regexAsterisk = "(?<!\\\\)\\*";
    final var regexQuestionMark = "(?<!\\\\)\\?";

    // Replace unescaped * with %
    final var patternAsterisk = Pattern.compile(regexAsterisk);
    final var matcherAsterisk = patternAsterisk.matcher(elasticsearchQuery);
    final var sqlQuery = new StringBuilder();
    while (matcherAsterisk.find()) {
      matcherAsterisk.appendReplacement(sqlQuery, "%");
    }
    matcherAsterisk.appendTail(sqlQuery);

    // Replace unescaped ? with _
    final var patternQuestionMark = Pattern.compile(regexQuestionMark);
    final var matcherQuestionMark = patternQuestionMark.matcher(sqlQuery.toString());
    final var finalSqlQuery = new StringBuilder();
    while (matcherQuestionMark.find()) {
      matcherQuestionMark.appendReplacement(finalSqlQuery, "_");
    }
    matcherQuestionMark.appendTail(finalSqlQuery);

    return finalSqlQuery.toString();
  }
}
