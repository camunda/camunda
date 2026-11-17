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
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;

public class LiteralSubstringStringTypeHandler extends StringTypeHandler {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final String parameter, final JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, transformParameter(parameter));
  }

  public static String transformParameter(final String parameter) {
    return "%" + parameter.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
