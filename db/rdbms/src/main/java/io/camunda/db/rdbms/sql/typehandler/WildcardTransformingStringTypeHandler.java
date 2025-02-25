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

  // Regular expression to match unescaped wildcards
  private static final String REGEX_ASTERISK = "(?<!\\\\)\\*";
  private static final String REGEX_QUESTION_MARK = "(?<!\\\\)\\?";
  private static final String REGEX_ESCAPED_ASTERISK = "\\\\\\*";
  private static final String REGEX_ESCAPED_QUESTION_MARK = "\\\\\\?";
  private static final String REGEX_PERCENT = "(?<!\\\\)%";
  private static final String REGEX_UNDERSCORE = "(?<!\\\\)_";
  private static final Pattern PATTERN_ASTERISK = Pattern.compile(REGEX_ASTERISK);
  private static final Pattern PATTERN_QUESTION_MARK = Pattern.compile(REGEX_QUESTION_MARK);
  private static final Pattern PATTERN_ESCAPED_ASTERISK = Pattern.compile(REGEX_ESCAPED_ASTERISK);
  private static final Pattern PATTERN_ESCAPED_QUESTION_MARK =
      Pattern.compile(REGEX_ESCAPED_QUESTION_MARK);
  private static final Pattern PATTERN_PERCENT = Pattern.compile(REGEX_PERCENT);
  private static final Pattern PATTERN_UNDERSCORE = Pattern.compile(REGEX_UNDERSCORE);

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final String parameter, final JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, transformParameter(parameter));
  }

  private static String replace(
      final Pattern pattern, final String input, final String replacement) {
    final var matcher = pattern.matcher(input);
    final var builder = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(builder, replacement);
    }
    matcher.appendTail(builder);
    return builder.toString();
  }

  public static String transformParameter(String parameter) {
    // Escape unescaped %
    parameter = replace(PATTERN_PERCENT, parameter, "\\\\%");
    // Escape unescaped _
    parameter = replace(PATTERN_UNDERSCORE, parameter, "\\\\_");

    // Replace unescaped * with %
    parameter = replace(PATTERN_ASTERISK, parameter, "%");
    // Replace unescaped ? with _
    parameter = replace(PATTERN_QUESTION_MARK, parameter, "_");

    // Unescape escaped *
    parameter = replace(PATTERN_ESCAPED_ASTERISK, parameter, "*");
    // Unescape escaped ?
    parameter = replace(PATTERN_ESCAPED_QUESTION_MARK, parameter, "?");

    return parameter;
  }
}
