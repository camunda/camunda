/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.decision;

import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;
import java.util.Map;

public class ZeebeDecisionInstanceDataDto implements RecordValue {
  private String evaluationFailureMessage;
  private String decisionName;
  private int decisionVersion;
  private String decisionRequirementsId;
  private long decisionRequirementsKey;
  private String decisionOutput;
  private String failedDecisionId;
  private Map<String, Object> variables;
  private String tenantId;
  private String bpmnProcessId;
  private long processInstanceKey;
  private String decisionId;
  private long decisionKey;
  private long elementInstanceKey;
  private long processDefinitionKey;
  private String elementId;
  private List<EvaluatedDecision> evaluatedDecisions;

  // Getters and setters

  public String getEvaluationFailureMessage() {
    return evaluationFailureMessage;
  }

  // Getters and setters for the main class

  public void setEvaluationFailureMessage(final String evaluationFailureMessage) {
    this.evaluationFailureMessage = evaluationFailureMessage;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public void setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
  }

  public int getDecisionVersion() {
    return decisionVersion;
  }

  public void setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public void setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public void setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
  }

  public String getDecisionOutput() {
    return decisionOutput;
  }

  public void setDecisionOutput(final String decisionOutput) {
    this.decisionOutput = decisionOutput;
  }

  public String getFailedDecisionId() {
    return failedDecisionId;
  }

  public void setFailedDecisionId(final String failedDecisionId) {
    this.failedDecisionId = failedDecisionId;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public void setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
  }

  public long getDecisionKey() {
    return decisionKey;
  }

  public void setDecisionKey(final long decisionKey) {
    this.decisionKey = decisionKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public List<EvaluatedDecision> getEvaluatedDecisions() {
    return evaluatedDecisions;
  }

  public void setEvaluatedDecisions(final List<EvaluatedDecision> evaluatedDecisions) {
    this.evaluatedDecisions = evaluatedDecisions;
  }

  @Override
  public String toString() {
    return "ZeebeDecisionInstanceDataDto{"
        + "evaluationFailureMessage='"
        + evaluationFailureMessage
        + '\''
        + ", decisionName='"
        + decisionName
        + '\''
        + ", decisionVersion="
        + decisionVersion
        + ", decisionRequirementsId='"
        + decisionRequirementsId
        + '\''
        + ", decisionRequirementsKey="
        + decisionRequirementsKey
        + ", decisionOutput='"
        + decisionOutput
        + '\''
        + ", failedDecisionId='"
        + failedDecisionId
        + '\''
        + ", variables="
        + (variables != null ? variables.toString() : "null")
        + ", tenantId='"
        + tenantId
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processInstanceKey="
        + processInstanceKey
        + ", decisionId='"
        + decisionId
        + '\''
        + ", decisionKey="
        + decisionKey
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", elementId='"
        + elementId
        + '\''
        + ", evaluatedDecisions="
        + (evaluatedDecisions != null ? evaluatedDecisions.toString() : "null")
        + '}';
  }

  public static class EvaluatedDecision {
    private String decisionName;
    private int decisionVersion;
    private String decisionType;
    private String decisionOutput;
    private String tenantId;
    private String decisionId;
    private long decisionKey;
    private List<EvaluatedInput> evaluatedInputs;
    private List<MatchedRule> matchedRules;

    // Getters and setters

    public String getDecisionName() {
      return decisionName;
    }

    public void setDecisionName(final String decisionName) {
      this.decisionName = decisionName;
    }

    public int getDecisionVersion() {
      return decisionVersion;
    }

    public void setDecisionVersion(final int decisionVersion) {
      this.decisionVersion = decisionVersion;
    }

    public String getDecisionType() {
      return decisionType;
    }

    public void setDecisionType(final String decisionType) {
      this.decisionType = decisionType;
    }

    public String getDecisionOutput() {
      return decisionOutput;
    }

    public void setDecisionOutput(final String decisionOutput) {
      this.decisionOutput = decisionOutput;
    }

    public String getTenantId() {
      return tenantId;
    }

    public void setTenantId(final String tenantId) {
      this.tenantId = tenantId;
    }

    public String getDecisionId() {
      return decisionId;
    }

    public void setDecisionId(final String decisionId) {
      this.decisionId = decisionId;
    }

    public long getDecisionKey() {
      return decisionKey;
    }

    public void setDecisionKey(final long decisionKey) {
      this.decisionKey = decisionKey;
    }

    public List<EvaluatedInput> getEvaluatedInputs() {
      return evaluatedInputs;
    }

    public void setEvaluatedInputs(final List<EvaluatedInput> evaluatedInputs) {
      this.evaluatedInputs = evaluatedInputs;
    }

    public List<MatchedRule> getMatchedRules() {
      return matchedRules;
    }

    public void setMatchedRules(final List<MatchedRule> matchedRules) {
      this.matchedRules = matchedRules;
    }

    @Override
    public String toString() {
      return "EvaluatedDecision{"
          + "decisionName='"
          + decisionName
          + '\''
          + ", decisionVersion="
          + decisionVersion
          + ", decisionType='"
          + decisionType
          + '\''
          + ", decisionOutput='"
          + decisionOutput
          + '\''
          + ", tenantId='"
          + tenantId
          + '\''
          + ", decisionId='"
          + decisionId
          + '\''
          + ", decisionKey="
          + decisionKey
          + ", evaluatedInputs="
          + (evaluatedInputs != null ? evaluatedInputs.toString() : "null")
          + ", matchedRules="
          + (matchedRules != null ? matchedRules.toString() : "null")
          + '}';
    }

    public static class EvaluatedInput {
      private String inputId;
      private String inputName;
      private String inputValue;

      // Getters and setters

      public String getInputId() {
        return inputId;
      }

      public void setInputId(final String inputId) {
        this.inputId = inputId;
      }

      public String getInputName() {
        return inputName;
      }

      public void setInputName(final String inputName) {
        this.inputName = inputName;
      }

      public String getInputValue() {
        return inputValue;
      }

      public void setInputValue(final String inputValue) {
        this.inputValue = inputValue;
      }

      @Override
      public String toString() {
        return "EvaluatedInput{"
            + "inputId='"
            + inputId
            + '\''
            + ", inputName='"
            + inputName
            + '\''
            + ", inputValue='"
            + inputValue
            + '\''
            + '}';
      }
    }

    public static class MatchedRule {
      private String ruleId;
      private int ruleIndex;
      private List<EvaluatedOutput> evaluatedOutputs;

      // Getters and setters

      public String getRuleId() {
        return ruleId;
      }

      public void setRuleId(final String ruleId) {
        this.ruleId = ruleId;
      }

      public int getRuleIndex() {
        return ruleIndex;
      }

      public void setRuleIndex(final int ruleIndex) {
        this.ruleIndex = ruleIndex;
      }

      public List<EvaluatedOutput> getEvaluatedOutputs() {
        return evaluatedOutputs;
      }

      public void setEvaluatedOutputs(final List<EvaluatedOutput> evaluatedOutputs) {
        this.evaluatedOutputs = evaluatedOutputs;
      }

      @Override
      public String toString() {
        return "MatchedRule{"
            + "ruleId='"
            + ruleId
            + '\''
            + ", ruleIndex="
            + ruleIndex
            + ", evaluatedOutputs="
            + (evaluatedOutputs != null ? evaluatedOutputs.toString() : "null")
            + '}';
      }

      public static class EvaluatedOutput {
        private String outputId;
        private String outputName;
        private String outputValue;

        // Getters and setters

        public String getOutputId() {
          return outputId;
        }

        public void setOutputId(final String outputId) {
          this.outputId = outputId;
        }

        public String getOutputName() {
          return outputName;
        }

        public void setOutputName(final String outputName) {
          this.outputName = outputName;
        }

        public String getOutputValue() {
          return outputValue;
        }

        public void setOutputValue(final String outputValue) {
          this.outputValue = outputValue;
        }

        @Override
        public String toString() {
          return "EvaluatedOutput{"
              + "outputId='"
              + outputId
              + '\''
              + ", outputName='"
              + outputName
              + '\''
              + ", outputValue='"
              + outputValue
              + '\''
              + '}';
        }
      }
    }
  }
}
