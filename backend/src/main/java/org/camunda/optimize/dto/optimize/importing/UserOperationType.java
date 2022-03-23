/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;

import static org.camunda.optimize.service.util.importing.EngineConstants.ACTIVATE_PROCESS_DEFINITION_OPERATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ACTIVATE_PROCESS_INSTANCE_OPERATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ACTIVATE_VIA_BATCH_OPERATION_TYPE;
import static org.camunda.optimize.service.util.importing.EngineConstants.INCL_INSTANCES_IN_DEFINITION_SUSPENSION_FIELD;
import static org.camunda.optimize.service.util.importing.EngineConstants.PROCESS_INSTANCE_ENTITY_TYPE;
import static org.camunda.optimize.service.util.importing.EngineConstants.SUSPEND_PROCESS_DEFINITION_OPERATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.SUSPEND_PROCESS_INSTANCE_OPERATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.SUSPEND_VIA_BATCH_OPERATION_TYPE;

public enum UserOperationType {
  SUSPEND_INSTANCE_BY_INSTANCE_ID(true),
  SUSPEND_INSTANCES_BY_DEFINITION_ID(true),
  SUSPEND_INSTANCES_BY_DEFINITION_KEY(true),
  SUSPEND_INSTANCES_VIA_BATCH(true),
  ACTIVATE_INSTANCE_BY_INSTANCE_ID(false),
  ACTIVATE_INSTANCES_BY_DEFINITION_ID(false),
  ACTIVATE_INSTANCES_BY_DEFINITION_KEY(false),
  ACTIVATE_INSTANCES_VIA_BATCH(false),
  NOT_SUSPENSION_RELATED_OPERATION(false);

  private final boolean suspendOperation;

  public boolean isSuspendOperation() {
    return suspendOperation;
  }

  UserOperationType(final Boolean suspendOperation) {
    this.suspendOperation = suspendOperation;
  }

  public static UserOperationType fromHistoricUserOperationLog(
    final HistoricUserOperationLogDto historicUserOperationLogDto) {
    if (isSuspendByInstanceIdOperation(historicUserOperationLogDto)) {
      return SUSPEND_INSTANCE_BY_INSTANCE_ID;
    } else if (isActivateByInstanceIdOperation(historicUserOperationLogDto)) {
      return ACTIVATE_INSTANCE_BY_INSTANCE_ID;
    } else if (isSuspendByDefinitionIdOperation(historicUserOperationLogDto)) {
      return SUSPEND_INSTANCES_BY_DEFINITION_ID;
    } else if (isActivateByDefinitionIdOperation(historicUserOperationLogDto)) {
      return ACTIVATE_INSTANCES_BY_DEFINITION_ID;
    } else if (isSuspendByDefinitionKeyOperation(historicUserOperationLogDto)) {
      return SUSPEND_INSTANCES_BY_DEFINITION_KEY;
    } else if (isActivateByDefinitionKeyOperation(historicUserOperationLogDto)) {
      return ACTIVATE_INSTANCES_BY_DEFINITION_KEY;
    } else if (isSuspendViaBatchOperation(historicUserOperationLogDto)) {
      return SUSPEND_INSTANCES_VIA_BATCH;
    } else if (isActivateViaBatchOperation(historicUserOperationLogDto)) {
      return ACTIVATE_INSTANCES_VIA_BATCH;
    } else {
      return NOT_SUSPENSION_RELATED_OPERATION;
    }
  }

  public static boolean isSuspensionByInstanceIdOperation(final UserOperationType userOpType) {
    return SUSPEND_INSTANCE_BY_INSTANCE_ID.equals(userOpType)
      || ACTIVATE_INSTANCE_BY_INSTANCE_ID.equals(userOpType);
  }

  public static boolean isSuspensionByDefinitionIdOperation(final UserOperationType userOpType) {
    return SUSPEND_INSTANCES_BY_DEFINITION_ID.equals(userOpType)
      || ACTIVATE_INSTANCES_BY_DEFINITION_ID.equals(userOpType);
  }

  public static boolean isSuspensionByDefinitionKeyOperation(final UserOperationType userOpType) {
    return SUSPEND_INSTANCES_BY_DEFINITION_KEY.equals(userOpType)
      || ACTIVATE_INSTANCES_BY_DEFINITION_KEY.equals(userOpType);
  }

  public static boolean isSuspensionViaBatchOperation(final UserOperationType userOpType) {
    return SUSPEND_INSTANCES_VIA_BATCH.equals(userOpType)
      || ACTIVATE_INSTANCES_VIA_BATCH.equals(userOpType);
  }

  private static boolean isSuspendByInstanceIdOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    return SUSPEND_PROCESS_INSTANCE_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
      && PROCESS_INSTANCE_ENTITY_TYPE.equalsIgnoreCase(historicUserOpLog.getEntityType())
      && historicUserOpLog.getProcessInstanceId() != null;
  }

  private static boolean isActivateByInstanceIdOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    return ACTIVATE_PROCESS_INSTANCE_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
      && PROCESS_INSTANCE_ENTITY_TYPE.equalsIgnoreCase(historicUserOpLog.getEntityType())
      && historicUserOpLog.getProcessInstanceId() != null;
  }

  private static boolean isSuspendByDefinitionIdOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    final boolean isSuspendProcessInstanceByDefinitionIdOperation =
      SUSPEND_PROCESS_INSTANCE_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && PROCESS_INSTANCE_ENTITY_TYPE.equalsIgnoreCase(historicUserOpLog.getEntityType())
        && historicUserOpLog.getProcessInstanceId() == null
        && historicUserOpLog.getProcessDefinitionId() != null;

    final boolean isSuspendProcessDefinitionByIdIncludingInstancesOperation =
      SUSPEND_PROCESS_DEFINITION_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && INCL_INSTANCES_IN_DEFINITION_SUSPENSION_FIELD.equalsIgnoreCase(historicUserOpLog.getProperty())
        && String.valueOf(true).equalsIgnoreCase(historicUserOpLog.getNewValue())
        && historicUserOpLog.getProcessDefinitionId() != null;

    return isSuspendProcessInstanceByDefinitionIdOperation
      || isSuspendProcessDefinitionByIdIncludingInstancesOperation;
  }

  private static boolean isActivateByDefinitionIdOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    final boolean isActivateProcessInstanceByDefinitionIdOperation =
      ACTIVATE_PROCESS_INSTANCE_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && PROCESS_INSTANCE_ENTITY_TYPE.equalsIgnoreCase(historicUserOpLog.getEntityType())
        && historicUserOpLog.getProcessInstanceId() == null
        && historicUserOpLog.getProcessDefinitionId() != null;

    final boolean isActivateProcessDefinitionByIdIncludingInstancesOperation =
      ACTIVATE_PROCESS_DEFINITION_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && INCL_INSTANCES_IN_DEFINITION_SUSPENSION_FIELD.equalsIgnoreCase(historicUserOpLog.getProperty())
        && String.valueOf(true).equalsIgnoreCase(historicUserOpLog.getNewValue())
        && historicUserOpLog.getProcessDefinitionId() != null;

    return isActivateProcessInstanceByDefinitionIdOperation
      || isActivateProcessDefinitionByIdIncludingInstancesOperation;
  }

  private static boolean isSuspendByDefinitionKeyOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    final boolean isSuspendProcessInstanceByDefinitionKeyOperation =
      SUSPEND_PROCESS_INSTANCE_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && PROCESS_INSTANCE_ENTITY_TYPE.equalsIgnoreCase(historicUserOpLog.getEntityType())
        && historicUserOpLog.getProcessInstanceId() == null
        && historicUserOpLog.getProcessDefinitionId() == null
        && historicUserOpLog.getProcessDefinitionKey() != null;

    final boolean isSuspendProcessDefinitionByKeyIncludingInstancesOperation =
      SUSPEND_PROCESS_DEFINITION_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && INCL_INSTANCES_IN_DEFINITION_SUSPENSION_FIELD.equalsIgnoreCase(historicUserOpLog.getProperty())
        && String.valueOf(true).equalsIgnoreCase(historicUserOpLog.getNewValue())
        && historicUserOpLog.getProcessDefinitionId() == null;

    return isSuspendProcessInstanceByDefinitionKeyOperation
      || isSuspendProcessDefinitionByKeyIncludingInstancesOperation;
  }

  private static boolean isActivateByDefinitionKeyOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    final boolean isActivateProcessInstanceByDefinitionKeyOperation =
      ACTIVATE_PROCESS_INSTANCE_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && PROCESS_INSTANCE_ENTITY_TYPE.equalsIgnoreCase(historicUserOpLog.getEntityType())
        && historicUserOpLog.getProcessInstanceId() == null
        && historicUserOpLog.getProcessDefinitionId() == null
        && historicUserOpLog.getProcessDefinitionKey() != null;

    final boolean isActivateProcessDefinitionByKeyIncludingInstancesOperation =
      ACTIVATE_PROCESS_DEFINITION_OPERATION.equalsIgnoreCase(historicUserOpLog.getOperationType())
        && INCL_INSTANCES_IN_DEFINITION_SUSPENSION_FIELD.equalsIgnoreCase(historicUserOpLog.getProperty())
        && String.valueOf(true).equalsIgnoreCase(historicUserOpLog.getNewValue())
        && historicUserOpLog.getProcessDefinitionId() == null;

    return isActivateProcessInstanceByDefinitionKeyOperation
      || isActivateProcessDefinitionByKeyIncludingInstancesOperation;
  }

  private static boolean isSuspendViaBatchOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    return SUSPEND_VIA_BATCH_OPERATION_TYPE.equalsIgnoreCase(historicUserOpLog.getOperationType());
  }

  private static boolean isActivateViaBatchOperation(final HistoricUserOperationLogDto historicUserOpLog) {
    return ACTIVATE_VIA_BATCH_OPERATION_TYPE.equalsIgnoreCase(historicUserOpLog.getOperationType());
  }
}