/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.record.ValueType;

public class ExporterPosition extends UnpackedObject implements DbValue {
  private final LongProperty positionProp = new LongProperty("exporterPosition");
  private final LongProperty jobSequenceProp = new LongProperty(ValueType.JOB.name(), 0);
  private final LongProperty deploymentSequenceProp =
      new LongProperty(ValueType.DEPLOYMENT.name(), 0);
  private final LongProperty processInstanceSequenceProp =
      new LongProperty(ValueType.PROCESS_INSTANCE.name(), 0);
  private final LongProperty incidentSequenceProp = new LongProperty(ValueType.INCIDENT.name(), 0);
  private final LongProperty messageSequenceProp = new LongProperty(ValueType.MESSAGE.name(), 0);
  private final LongProperty messageSubscriptionSequenceProp =
      new LongProperty(ValueType.MESSAGE_SUBSCRIPTION.name(), 0);
  private final LongProperty processMessageSubscriptionSequenceProp =
      new LongProperty(ValueType.PROCESS_MESSAGE_SUBSCRIPTION.name(), 0);
  private final LongProperty jobBatchSequenceProp = new LongProperty(ValueType.JOB_BATCH.name(), 0);
  private final LongProperty variableSequenceProp = new LongProperty(ValueType.VARIABLE.name(), 0);
  private final LongProperty variableDocumentSequenceProp =
      new LongProperty(ValueType.VARIABLE_DOCUMENT.name(), 0);
  private final LongProperty processInstanceCreationSequenceProp =
      new LongProperty(ValueType.PROCESS_INSTANCE_CREATION.name(), 0);
  private final LongProperty errorSequenceProp = new LongProperty(ValueType.ERROR.name(), 0);
  private final LongProperty processSequenceProp = new LongProperty(ValueType.PROCESS.name(), 0);
  private final LongProperty decisionSequenceProp = new LongProperty(ValueType.DECISION.name(), 0);
  private final LongProperty decisionRequirementsSequenceProp =
      new LongProperty(ValueType.DECISION_REQUIREMENTS.name(), 0);
  private final LongProperty decisionEvaluationSequenceProp =
      new LongProperty(ValueType.DECISION_EVALUATION.name(), 0);

  public ExporterPosition() {
    declareProperty(positionProp)
        .declareProperty(jobSequenceProp)
        .declareProperty(deploymentSequenceProp)
        .declareProperty(processInstanceSequenceProp)
        .declareProperty(incidentSequenceProp)
        .declareProperty(messageSequenceProp)
        .declareProperty(messageSubscriptionSequenceProp)
        .declareProperty(processMessageSubscriptionSequenceProp)
        .declareProperty(jobBatchSequenceProp)
        .declareProperty(variableSequenceProp)
        .declareProperty(variableDocumentSequenceProp)
        .declareProperty(processInstanceCreationSequenceProp)
        .declareProperty(errorSequenceProp)
        .declareProperty(processSequenceProp)
        .declareProperty(decisionSequenceProp)
        .declareProperty(decisionRequirementsSequenceProp)
        .declareProperty(decisionEvaluationSequenceProp);
  }

  public void wrap(final ExporterPosition exporter) {
    positionProp.setValue(exporter.getPosition());
    jobSequenceProp.setValue(exporter.getSequence(ValueType.JOB));
    deploymentSequenceProp.setValue(exporter.getSequence(ValueType.DEPLOYMENT));
    processInstanceSequenceProp.setValue(exporter.getSequence(ValueType.PROCESS_INSTANCE));
    incidentSequenceProp.setValue(exporter.getSequence(ValueType.INCIDENT));
    messageSequenceProp.setValue(exporter.getSequence(ValueType.MESSAGE));
    messageSubscriptionSequenceProp.setValue(exporter.getSequence(ValueType.MESSAGE_SUBSCRIPTION));
    jobBatchSequenceProp.setValue(exporter.getSequence(ValueType.JOB_BATCH));
    variableSequenceProp.setValue(exporter.getSequence(ValueType.VARIABLE));
    variableDocumentSequenceProp.setValue(exporter.getSequence(ValueType.VARIABLE_DOCUMENT));
    processInstanceCreationSequenceProp.setValue(
        exporter.getSequence(ValueType.PROCESS_INSTANCE_CREATION));
    errorSequenceProp.setValue(exporter.getSequence(ValueType.ERROR));
    processSequenceProp.setValue(exporter.getSequence(ValueType.PROCESS));
    decisionSequenceProp.setValue(exporter.getSequence(ValueType.DECISION));
    decisionRequirementsSequenceProp.setValue(
        exporter.getSequence(ValueType.DECISION_REQUIREMENTS));
    decisionEvaluationSequenceProp.setValue(exporter.getSequence(ValueType.DECISION_EVALUATION));
  }

  public void setPosition(final long position) {
    positionProp.setValue(position);
  }

  public long getPosition() {
    return positionProp.getValue();
  }

  public void setSequence(final String valueType, final long sequence) {
    setSequence(Enum.valueOf(ValueType.class, valueType), sequence);
  }

  public void setSequence(final ValueType valueType, final long sequence) {
    switch (valueType) {
      case JOB:
        {
          jobSequenceProp.setValue(sequence);
          break;
        }
      case DEPLOYMENT:
        {
          deploymentSequenceProp.setValue(sequence);
          break;
        }
      case PROCESS_INSTANCE:
        {
          processInstanceSequenceProp.setValue(sequence);
          break;
        }
      case INCIDENT:
        {
          incidentSequenceProp.setValue(sequence);
          break;
        }
      case MESSAGE:
        {
          messageSequenceProp.setValue(sequence);
          break;
        }
      case MESSAGE_SUBSCRIPTION:
        {
          messageSubscriptionSequenceProp.setValue(sequence);
          break;
        }
      case PROCESS_MESSAGE_SUBSCRIPTION:
        {
          processMessageSubscriptionSequenceProp.setValue(sequence);
          break;
        }
      case JOB_BATCH:
        {
          jobBatchSequenceProp.setValue(sequence);
          break;
        }
      case VARIABLE:
        {
          variableSequenceProp.setValue(sequence);
          break;
        }
      case VARIABLE_DOCUMENT:
        {
          variableDocumentSequenceProp.setValue(sequence);
          break;
        }
      case PROCESS_INSTANCE_CREATION:
        {
          processInstanceCreationSequenceProp.setValue(sequence);
          break;
        }
      case ERROR:
        {
          errorSequenceProp.setValue(sequence);
          break;
        }
      case PROCESS:
        {
          processSequenceProp.setValue(sequence);
          break;
        }
      case DECISION:
        {
          decisionSequenceProp.setValue(sequence);
          break;
        }
      case DECISION_REQUIREMENTS:
        {
          decisionRequirementsSequenceProp.setValue(sequence);
          break;
        }
      case DECISION_EVALUATION:
        {
          decisionEvaluationSequenceProp.setValue(sequence);
          break;
        }
      default:
        throw new IllegalArgumentException("Unexpected value: " + valueType);
    }
  }

  public long getSequence(final String valueType) {
    return getSequence(Enum.valueOf(ValueType.class, valueType));
  }

  public long getSequence(final ValueType valueType) {
    switch (valueType) {
      case JOB:
        {
          return jobSequenceProp.getValue();
        }
      case DEPLOYMENT:
        {
          return deploymentSequenceProp.getValue();
        }
      case PROCESS_INSTANCE:
        {
          return processInstanceSequenceProp.getValue();
        }
      case INCIDENT:
        {
          return incidentSequenceProp.getValue();
        }
      case MESSAGE:
        {
          return messageSequenceProp.getValue();
        }
      case MESSAGE_SUBSCRIPTION:
        {
          return messageSubscriptionSequenceProp.getValue();
        }
      case PROCESS_MESSAGE_SUBSCRIPTION:
        {
          return processMessageSubscriptionSequenceProp.getValue();
        }
      case JOB_BATCH:
        {
          return jobBatchSequenceProp.getValue();
        }
      case VARIABLE:
        {
          return variableSequenceProp.getValue();
        }
      case VARIABLE_DOCUMENT:
        {
          return variableDocumentSequenceProp.getValue();
        }
      case PROCESS_INSTANCE_CREATION:
        {
          return processInstanceCreationSequenceProp.getValue();
        }
      case ERROR:
        {
          return errorSequenceProp.getValue();
        }
      case PROCESS:
        {
          return processSequenceProp.getValue();
        }
      case DECISION:
        {
          return decisionSequenceProp.getValue();
        }
      case DECISION_REQUIREMENTS:
        {
          return decisionRequirementsSequenceProp.getValue();
        }
      case DECISION_EVALUATION:
        {
          return decisionEvaluationSequenceProp.getValue();
        }
      default:
        throw new IllegalArgumentException("Unexpected value: " + valueType);
    }
  }
}
