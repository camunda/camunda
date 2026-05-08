/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Link} from 'react-router-dom';
import {Paths} from 'modules/Routes';

/**
 * Map of raw V2 API field names → human-readable column headers. Falls back
 * to a Title Case version of the raw key when a field isn't in the map.
 */
const COLUMN_LABELS: Record<string, string> = {
  processInstanceKey: 'Instance',
  parentProcessInstanceKey: 'Parent instance',
  processDefinitionId: 'Process',
  processDefinitionKey: 'Process key',
  processDefinitionVersion: 'Version',
  incidentKey: 'Incident',
  jobKey: 'Job',
  userTaskKey: 'Task',
  elementId: 'Element',
  elementInstanceKey: 'Element instance',
  errorType: 'Error type',
  errorMessage: 'Message',
  creationTime: 'Created',
  creationDate: 'Created',
  completionDate: 'Completed',
  startDate: 'Started',
  endDate: 'Ended',
  dueDate: 'Due',
  followUpDate: 'Follow-up',
  deadline: 'Deadline',
  assignee: 'Assignee',
  candidateUser: 'Candidate user',
  candidateGroup: 'Candidate group',
  state: 'State',
  type: 'Type',
  name: 'Name',
  retries: 'Retries',
  priority: 'Priority',
  tenantId: 'Tenant',
  bpmnProcessId: 'BPMN process',
  version: 'Version',
};

function toTitleCase(s: string): string {
  return s
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]/g, ' ')
    .replace(
      /\w\S*/g,
      (w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase(),
    );
}

function columnLabel(field: string): string {
  return COLUMN_LABELS[field] ?? toTitleCase(field);
}

/**
 * Format an ISO date string as a short, readable representation. Returns the
 * raw value if parsing fails so we never silently hide data.
 */
function formatDate(value: unknown): string {
  if (value == null || value === '') {
    return '';
  }
  const t = new Date(value as string);
  if (isNaN(t.getTime())) {
    return String(value);
  }
  // Short locale-independent form: "2026-05-08 12:34"
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())} ` +
    `${pad(t.getHours())}:${pad(t.getMinutes())}`
  );
}

const DATE_FIELDS = new Set([
  'creationTime',
  'creationDate',
  'completionDate',
  'startDate',
  'endDate',
  'dueDate',
  'followUpDate',
  'deadline',
]);

const KEY_FIELDS_LINKED_TO_INSTANCE = new Set([
  'processInstanceKey',
  'parentProcessInstanceKey',
]);

/**
 * Truncate long error messages to a reasonable preview, keeping the table
 * compact. Full text is available in the hover tooltip via the title attr.
 */
const MAX_MESSAGE_LEN = 80;

/**
 * Render a single table cell value. Renders as:
 *  - a router-link to the Operate process instance page for keys
 *    (processInstanceKey, parentProcessInstanceKey)
 *  - a formatted timestamp for known date fields
 *  - a truncated preview (with title=full text) for errorMessage
 *  - the raw stringified value otherwise
 *
 * Pure presentation — no fetching, no state. Centralised here so both the
 * regular table widget and the hero/embedded variants format consistently.
 */
function renderCellValue(
  field: string,
  value: unknown,
  row: Record<string, unknown>,
): React.ReactNode {
  if (value == null || value === '') {
    return <span style={{color: 'var(--cds-text-helper)'}}>—</span>;
  }

  // Linkable instance keys (or the row's own processInstanceKey if the
  // table is itself a list of incidents/jobs/tasks).
  if (KEY_FIELDS_LINKED_TO_INSTANCE.has(field)) {
    const id = String(value);
    return (
      <Link to={Paths.processInstance(id)} title={`Open instance ${id}`}>
        {id}
      </Link>
    );
  }

  // For incident/job/task tables: when the row carries a processInstanceKey,
  // make incidentKey / jobKey / userTaskKey visible AND link to the parent
  // instance page (Operate doesn't have first-class incident/job pages, but
  // the parent instance page is where you'd triage the row).
  if (
    (field === 'incidentKey' ||
      field === 'jobKey' ||
      field === 'userTaskKey' ||
      field === 'elementInstanceKey') &&
    row.processInstanceKey != null
  ) {
    const parentId = String(row.processInstanceKey);
    return (
      <Link
        to={Paths.processInstance(parentId)}
        title={`Open parent instance ${parentId}`}
      >
        {String(value)}
      </Link>
    );
  }

  if (DATE_FIELDS.has(field)) {
    return formatDate(value);
  }

  const stringValue = String(value);

  if (field === 'errorMessage' && stringValue.length > MAX_MESSAGE_LEN) {
    return (
      <span title={stringValue}>{stringValue.slice(0, MAX_MESSAGE_LEN)}…</span>
    );
  }

  return stringValue;
}

export {columnLabel, renderCellValue};
