/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.10';
import type {
  AuditLog,
  AuditLogOperationType,
} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {Api, User} from '@carbon/react/icons';
import {Paths} from 'modules/Routes';

const INVALID_PROCESS_INSTANCE_KEY = '-1';

const formatBatchTitle = (batchOperationType?: BatchOperationType) => {
  switch (batchOperationType) {
    case 'DELETE_PROCESS_INSTANCE':
    case 'CANCEL_PROCESS_INSTANCE':
    case 'MIGRATE_PROCESS_INSTANCE':
    case 'MODIFY_PROCESS_INSTANCE':
      return 'process instances';
    case 'RESOLVE_INCIDENT':
      return 'incidents';
    case 'DELETE_DECISION_DEFINITION':
      return 'decisions';
    case 'DELETE_PROCESS_DEFINITION':
      return 'process definitions';
    case 'ADD_VARIABLE':
    case 'UPDATE_VARIABLE':
      return 'variables';
    default:
      return undefined;
  }
};

const formatModalHeading = (auditLog: AuditLog) => {
  return `${spaceAndCapitalize(
    auditLog.operationType,
  )} ${spaceAndCapitalize(auditLog.entityType)}`;
};

const getActorIcon = (auditLog: AuditLog) => {
  switch (auditLog.actorType) {
    case 'USER':
      return User;
    case 'CLIENT':
      return Api;
    default:
      return null;
  }
};

const isValidProcessInstanceKey = (
  processInstanceKey?: string | null,
): processInstanceKey is string => {
  return (
    Boolean(processInstanceKey) &&
    processInstanceKey !== INVALID_PROCESS_INSTANCE_KEY
  );
};

const mapToCellEntityKeyData = (
  item: AuditLog,
  processDefinitionName?: string | null,
  decisionDefinitionName?: string | null,
  tasklistUrl?: string,
): {
  name?: string | null;
  link?: string;
  linkLabel?: string;
  label?: string | null;
} => {
  switch (item.entityType) {
    case 'BATCH':
      return {
        link: Paths.batchOperation(item.batchOperationKey),
        linkLabel: `View batch operation ${item.batchOperationKey}`,
        label: item.batchOperationKey,
      };
    case 'PROCESS_INSTANCE':
      return {
        name: processDefinitionName,
        link: Paths.processInstance(item.entityKey),
        linkLabel: `View process instance ${item.entityKey}`,
        label: item.entityKey,
      };
    case 'DECISION':
      if (item.operationType === 'EVALUATE') {
        return {
          name: decisionDefinitionName,
          link: Paths.decisionInstance(item.entityKey),
          linkLabel: `View decision instance ${item.entityKey}`,
          label: item.entityKey,
        };
      } else {
        return {name: decisionDefinitionName, label: item.entityKey};
      }
    case 'USER_TASK':
      return {
        link: tasklistUrl ? `${tasklistUrl}/${item.entityKey}` : undefined,
        linkLabel: `View user task ${item.entityKey}`,
        label: item.entityKey,
      };
    default:
      return {
        label: item.entityKey,
      };
  }
};

const userTaskOperations: Set<AuditLogOperationType> = new Set([
  'ASSIGN',
  'UNASSIGN',
]);

const mapToCellDetailsData = (
  item: AuditLog,
): {property?: string; value?: string | null} => {
  if (item.entityType === 'BATCH' && item.batchOperationType) {
    return {
      property: 'Batch operation type',
      value: spaceAndCapitalize(item.batchOperationType),
    };
  } else if (
    item.entityType === 'USER_TASK' &&
    userTaskOperations.has(item.operationType)
  ) {
    return {
      property: 'Assignee',
      value: item.relatedEntityKey,
    };
  } else if (item.entityType === 'RESOURCE' && item.resourceKey) {
    return {
      property: 'Resource key',
      value: item.resourceKey,
    };
  } else {
    return {};
  }
};

export {
  formatBatchTitle,
  formatModalHeading,
  getActorIcon,
  isValidProcessInstanceKey,
  mapToCellEntityKeyData,
  mapToCellDetailsData,
};
