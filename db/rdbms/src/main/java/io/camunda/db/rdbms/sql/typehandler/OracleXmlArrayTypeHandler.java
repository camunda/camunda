/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Type handler for Oracle that converts List&lt;Long&gt; to XML format for use with XMLTABLE. This
 * allows efficient handling of large lists without hitting Oracle's 1000-item IN clause limit.
 *
 * <p>The list is converted to XML format:
 * &lt;d&gt;&lt;r&gt;1&lt;/r&gt;&lt;r&gt;2&lt;/r&gt;&lt;/d&gt;
 *
 * <p>Usage in MyBatis XML:
 *
 * <pre>
 * WHERE column IN (
 *   SELECT COLUMN_VALUE
 *   FROM XMLTABLE('/d/r'
 *        PASSING XMLTYPE(#{list, typeHandler=io.camunda.db.rdbms.sql.typehandler.OracleXmlArrayTypeHandler})
 *        COLUMNS COLUMN_VALUE NUMBER PATH 'text()')
 * )
 * </pre>
 */
public class OracleXmlArrayTypeHandler extends BaseTypeHandler<List<Long>> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final List<Long> parameter, final JdbcType jdbcType)
      throws SQLException {
    if (parameter.isEmpty()) {
      // For empty lists, create an XML with a single null element to prevent SQL errors
      ps.setString(i, "<d><r></r></d>");
      return;
    }

    final StringBuilder xml = new StringBuilder("<d>");
    for (final Long value : parameter) {
      xml.append("<r>").append(value).append("</r>");
    }
    xml.append("</d>");
    ps.setString(i, xml.toString());
  }

  @Override
  public List<Long> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    final String xml = rs.getString(columnName);
    return parseXml(xml);
  }

  @Override
  public List<Long> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    final String xml = rs.getString(columnIndex);
    return parseXml(xml);
  }

  @Override
  public List<Long> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    final String xml = cs.getString(columnIndex);
    return parseXml(xml);
  }

  private List<Long> parseXml(final String xml) {
    if (xml == null || xml.isEmpty()) {
      return null;
    }

    final List<Long> result = new ArrayList<>();
    int startIndex = 0;

    // Simple XML parsing for <r>value</r> pattern
    while (true) {
      final int openTag = xml.indexOf("<r>", startIndex);
      if (openTag == -1) {
        break;
      }

      final int closeTag = xml.indexOf("</r>", openTag);
      if (closeTag == -1) {
        break;
      }

      final String value = xml.substring(openTag + 3, closeTag).trim();
      if (!value.isEmpty()) {
        result.add(Long.parseLong(value));
      }

      startIndex = closeTag + 4;
    }

    return result.isEmpty() ? null : result;
  }
}
