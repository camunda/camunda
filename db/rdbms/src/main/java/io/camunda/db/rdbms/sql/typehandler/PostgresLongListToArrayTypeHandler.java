/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis type handler that maps between a Java {@link java.util.List} of {@link java.lang.Long}
 * values and a PostgreSQL {@code BIGINT[]} column.
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>When writing parameters, the handler creates a JDBC {@link java.sql.Array} of type {@code
 *       "bigint"} from the provided {@code List&lt;Long&gt;} and binds it to the prepared
 *       statement.
 *   <li>When reading results, the handler converts the JDBC {@link java.sql.Array} back into an
 *       immutable {@code List&lt;Long&gt;} instance. If the underlying SQL value is {@code NULL},
 *       this method returns {@code null}.
 * </ul>
 *
 * <p>Typical MyBatis usage:
 *
 * <pre>{@code
 * <!-- Parameter mapping -->
 * <insert id="insertExample" parameterType="map">
 *   INSERT INTO example_table (id_list)
 *   VALUES (#{ids, typeHandler=io.camunda.db.rdbms.sql.typehandler.PostgresLongListToArrayTypeHandler})
 * </insert>
 *
 * <!-- Result mapping -->
 * <resultMap id="exampleResultMap" type="Example">
 *   <result property="ids"
 *           column="id_list"
 *           typeHandler="io.camunda.db.rdbms.sql.typehandler.PostgresLongListToArrayTypeHandler"/>
 * </resultMap>
 * }</pre>
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class PostgresLongListToArrayTypeHandler extends BaseTypeHandler<List<Long>> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final List<Long> parameter, final JdbcType jdbcType)
      throws SQLException {
    final Array array = ps.getConnection().createArrayOf("bigint", parameter.toArray());
    try {
      ps.setArray(i, array);
    } finally {
      try {
        array.free();
      } catch (final SQLException ignored) {
        // ignore exception on free to avoid masking earlier exceptions
      }
    }
  }

  @Override
  public List<Long> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<Long> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<Long> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private List<Long> toList(final Array array) throws SQLException {
    if (array == null) {
      return null;
    }
    try {
      return List.of((Long[]) array.getArray());
    } finally {
      try {
        array.free();
      } catch (final SQLException ignored) {
        // ignore exception on free to avoid masking earlier exceptions
      }
    }
  }
}
