/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.List;
import java.util.Optional;

/**
 * An agent element of one of the demo BPMN templates: what deployment turns into an agent
 * definition, and the tools its runs have.
 *
 * <p>Only the ad-hoc sub-process agent has BPMN tool elements. The AI Agent Task and External Agent
 * templates model no tool elements, so their tools carry no element ID and their tool calls only
 * show up in the run's total.
 */
record AgentElement(
    String processId,
    String elementId,
    String name,
    AgentDefinitionType type,
    List<AgentTool> tools) {

  static final List<AgentElement> ALL =
      List.of(
          new AgentElement(
              "customer-onboarding",
              "ai-document-review-agent",
              "Document review agent",
              AgentDefinitionType.AI_AGENT_SUB_PROCESS,
              List.of(
                  new AgentTool(
                      "extract_data",
                      "Extract structured data from a document",
                      "extract-data-task"),
                  new AgentTool(
                      "validate_fields",
                      "Validate fields against business rules",
                      "validate-fields-task"),
                  new AgentTool("lookup_record", "Look up a record by key", "lookup-record-task"),
                  new AgentTool(
                      "submit_decision",
                      "Submit decision to downstream system",
                      "submit-decision-task"),
                  new AgentTool("MCP_crm___get_customer", "Retrieve customer data from CRM"))),
          new AgentElement(
              "loan-approval",
              "risk-assessment-agent-task",
              "Risk assessment agent",
              AgentDefinitionType.AI_AGENT_TASK,
              List.of(
                  new AgentTool(
                      "credit_bureau_lookup", "Query external credit bureau for score history"),
                  new AgentTool(
                      "fraud_score_check", "Run fraud heuristics against the applicant profile"))),
          new AgentElement(
              "fraud-dispute-handling",
              "external-fraud-review-agent-task",
              "External fraud review agent",
              AgentDefinitionType.EXTERNAL_AGENT,
              List.of(
                  new AgentTool(
                      "transaction_history_lookup",
                      "Retrieve full transaction history for the disputed charge"),
                  new AgentTool(
                      "merchant_risk_check",
                      "Check merchant risk score against partner database"))));

  static Optional<AgentElement> forProcess(final String processId) {
    return ALL.stream().filter(agent -> agent.processId().equals(processId)).findFirst();
  }

  static Optional<AgentElement> forElement(final String elementId) {
    return ALL.stream().filter(agent -> agent.elementId().equals(elementId)).findFirst();
  }
}
