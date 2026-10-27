/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.camunda.bpm.model.dmn.DmnModelException;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DmnModelUtilTest {

  private static final String DMN_V1 =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
         xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
         xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
         xmlns:biodi="http://bpmn.io/schema/dmn/biodi/2.0"
         id="force_users" name="Force Users"
         namespace="http://camunda.org/schema/1.0/dmn" exporter="Camunda Modeler"
         exporterVersion="4.11.0">
        <decision id="jedi_or_sith" name="Jedi or Sith">
          <decisionTable id="DecisionTable_14n3bxx">
            <input id="Input_1" label="Lightsaber color" biodi:width="192">
              <inputExpression id="InputExpression_1" typeRef="string">
                <text>lightsaberColor</text>
              </inputExpression>
            </input>
            <output id="Output_1" label="Jedi or Sith" name="jedi_or_sith">
              <outputValues id="UnaryTests_0hj346a">
                <text>"Jedi","Sith"</text>
              </outputValues>
            </output>
            <rule id="DecisionRule_0zumznl">
              <inputEntry id="UnaryTests_0leuxqi">
                <text>"blue"</text>
              </inputEntry>
              <outputEntry id="LiteralExpression_0c9vpz8">
                <text>"Jedi"</text>
              </outputEntry>
            </rule>
          </decisionTable>
        </decision>
      </definitions>
      """;

  @Test
  void shouldParseValidXml() {
    // given

    // when
    final var result = DmnModelUtil.parseDmnModel(DMN_V1);

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void shouldThrowExceptionWhenParsingInvalidXml() {
    // given
    final String dmnXml = "<invalid></invalid>";

    // when

    // then
    assertThatThrownBy(() -> DmnModelUtil.parseDmnModel(dmnXml))
        .isInstanceOf(DmnModelException.class)
        .hasMessage("Unable to parse model");
  }

  @Test
  void shouldExtractDecisionNameWhenExists() {
    // given
    final String decisionKey = "jedi_or_sith";

    // when
    final Optional<String> result = DmnModelUtil.extractDecisionDefinitionName(decisionKey, DMN_V1);

    // then
    assertThat(result).isPresent().get().isEqualTo("Jedi or Sith");
  }

  @Test
  void shouldReturnEmptyWhenDecisionKeyNotFound() {
    // given
    final String decisionKey = "nonExistentDecision";
    final String dmnXml =
        "<dmn:definitions xmlns:dmn=\"https://www.omg.org/spec/DMN/20191111/MODEL/\">"
            + "  <dmn:decision id=\"decision1\" name=\"Test Decision\"/>"
            + "</dmn:definitions>";

    // when
    final Optional<String> result = DmnModelUtil.extractDecisionDefinitionName(decisionKey, dmnXml);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldThrowExceptionWhenInputDecisionKeyIsNull() {
    // when
    final DmnModelInstance model = Mockito.mock(DmnModelInstance.class);

    // then
    assertThatThrownBy(() -> DmnModelUtil.extractInputVariables(model, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("decisionKey must not be null");
  }

  @Test
  void shouldThrowExceptionWhenOutputDecisionKeyIsNull() {
    // when
    final DmnModelInstance model = Mockito.mock(DmnModelInstance.class);

    // then
    assertThatThrownBy(() -> DmnModelUtil.extractOutputVariables(model, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("decisionKey must not be null");
  }
}
