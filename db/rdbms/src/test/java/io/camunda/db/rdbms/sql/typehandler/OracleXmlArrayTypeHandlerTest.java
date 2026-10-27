/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleXmlArrayTypeHandlerTest {

  private final OracleXmlArrayTypeHandler handler = new OracleXmlArrayTypeHandler();

  @Test
  void shouldConvertListToXml() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final List<Long> input = List.of(1L, 2L, 3L);

    handler.setNonNullParameter(ps, 1, input, null);

    verify(ps).setString(1, "<d><r>1</r><r>2</r><r>3</r></d>");
  }

  @Test
  void shouldConvertSingleItemListToXml() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final List<Long> input = List.of(42L);

    handler.setNonNullParameter(ps, 1, input, null);

    verify(ps).setString(1, "<d><r>42</r></d>");
  }

  @Test
  void shouldConvertEmptyListToXml() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final List<Long> input = List.of();

    handler.setNonNullParameter(ps, 1, input, null);

    verify(ps).setString(1, "<d><r></r></d>");
  }

  @Test
  void shouldConvertLargeListToXml() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final List<Long> input = List.of(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L);

    handler.setNonNullParameter(ps, 1, input, null);

    final String expected =
        "<d><r>100</r><r>200</r><r>300</r><r>400</r><r>500</r>"
            + "<r>600</r><r>700</r><r>800</r><r>900</r><r>1000</r></d>";
    verify(ps).setString(1, expected);
  }

  @Test
  void shouldConvertXmlToList() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString("col")).thenReturn("<d><r>10</r><r>20</r><r>30</r></d>");

    final List<Long> result = handler.getNullableResult(rs, "col");

    assertThat(result).containsExactly(10L, 20L, 30L);
  }

  @Test
  void shouldConvertXmlWithWhitespaceToList() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString("col")).thenReturn("<d>\n  <r>10</r>\n  <r>20</r>\n</d>");

    final List<Long> result = handler.getNullableResult(rs, "col");

    assertThat(result).containsExactly(10L, 20L);
  }

  @Test
  void shouldReturnNullForNullXml() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString(1)).thenReturn(null);

    final List<Long> result = handler.getNullableResult(rs, 1);

    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForEmptyXml() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString(1)).thenReturn("");

    final List<Long> result = handler.getNullableResult(rs, 1);

    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForXmlWithOnlyEmptyElement() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString("col")).thenReturn("<d><r></r></d>");

    final List<Long> result = handler.getNullableResult(rs, "col");

    assertThat(result).isNull();
  }

  @Test
  void shouldHandleLargeNumbers() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final List<Long> input = List.of(Long.MAX_VALUE, Long.MIN_VALUE, 0L);

    handler.setNonNullParameter(ps, 1, input, null);

    verify(ps)
        .setString(1, "<d><r>" + Long.MAX_VALUE + "</r><r>" + Long.MIN_VALUE + "</r><r>0</r></d>");
  }

  @Test
  void shouldRoundTripConversion() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final ResultSet rs = mock(ResultSet.class);
    final List<Long> original = List.of(123L, 456L, 789L, 1011L);

    handler.setNonNullParameter(ps, 1, original, null);

    final String expectedXml = "<d><r>123</r><r>456</r><r>789</r><r>1011</r></d>";
    verify(ps).setString(1, expectedXml);

    when(rs.getString("col")).thenReturn(expectedXml);
    final List<Long> result = handler.getNullableResult(rs, "col");

    assertThat(result).isEqualTo(original);
  }
}
