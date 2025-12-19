/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo, useState} from 'react';
import {useOutletContext} from 'react-router-dom';
import {
  SkeletonText,
  DataTable,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableExpandRow,
  TableExpandHeader,
  TableExpandedRow,
  Tag,
} from '@carbon/react';
import {formatISODateTime} from 'common/dates/formatDateRelative';
import {formatDate} from 'common/dates/formatDate';
import {getPriorityLabel} from 'common/tasks/getPriorityLabel';
import {useTaskHistory, type TaskHistoryOperation} from 'v2/api/useTaskHistory.query';
import type {OutletContext} from 'v2/TaskDetailsLayout';
import styles from './styles.module.scss';

const OPERATION_TYPE_LABELS: Record<string, string> = {
  COMPLETE: 'Complete task',
  ASSIGN: 'Assign task',
  UNASSIGN: 'Unassign task',
  UPDATE: 'Update task',
};

const formatTaskCardRelativeDate = (dateString: string) => {
  const formatted = formatISODateTime(dateString);

  if (formatted === null) {
    return dateString;
  }

  return formatted.relative.text;
};

const formatPropertyValue = (
  value: string | undefined,
  propertyName?: string,
): string => {
  if (value === undefined || value === null || value === '') {
    return '—';
  }

  // Convert priority numbers to labels
  if (propertyName === 'Priority' && typeof value === 'string') {
    const priorityNumber = Number(value);
    if (!isNaN(priorityNumber)) {
      const priorityLabel = getPriorityLabel(priorityNumber);
      return priorityLabel.short;
    }
  }

  // Check if the value looks like an ISO date string
  const isoDateRegex = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/;
  if (typeof value === 'string' && isoDateRegex.test(value)) {
    try {
      // Use formatDate for a more readable date format
      const formatted = formatDate(value, true);
      return formatted || value;
    } catch {
      // Fallback to relative date if formatDate fails
      const relativeFormatted = formatISODateTime(value);
      if (relativeFormatted) {
        return relativeFormatted.relative.text;
      }
    }
  }

  return value;
};

const formatDetailsSummary = (entry: TaskHistoryOperation): string => {
  const {operationType, details} = entry;

  switch (operationType) {
    case 'COMPLETE':
      return '—';
    case 'ASSIGN':
      if (details?.newValue && details?.oldValue) {
        return `Assigned to ${details.newValue}`;
      }
      if (details?.newValue) {
        return `Assigned to ${details.newValue}`;
      }
      return '—';
    case 'UNASSIGN':
      if (details?.oldValue) {
        return `Unassigned from ${details.oldValue}`;
      }
      return '—';
    case 'UPDATE':
      if (details?.properties && details.properties.length > 0) {
        // Show property names separated by commas
        return details.properties.map((p) => p.property).join(', ');
      }
      // Legacy: single property support
      if (details?.property) {
        return details.property;
      }
      return '—';
    default:
      return '—';
  }
};

const formatDetailsExpanded = (entry: TaskHistoryOperation): React.ReactNode => {
  const {operationType, details} = entry;

  switch (operationType) {
    case 'COMPLETE':
      return null;
    case 'ASSIGN':
      const oldAssignee = details?.oldValue;
      const newAssignee = details?.newValue;
      const oldAssigneeDisplay = oldAssignee || 'No assignee';
      const newAssigneeDisplay = newAssignee || 'No assignee';
      return (
        <div>
          <div style={{fontSize: '0.875rem', color: 'var(--cds-text-secondary)'}}>
            <span style={{fontWeight: 'bold'}}>Assignee:</span> From{' '}
            <Tag size="sm" type="gray">
              {oldAssigneeDisplay}
            </Tag>
            to{' '}
            <Tag size="sm" type="gray">
              {newAssigneeDisplay}
            </Tag>
          </div>
        </div>
      );
    case 'UNASSIGN':
      const previousAssignee = details?.oldValue || 'No assignee';
      return (
        <div>
          <div style={{fontSize: '0.875rem', color: 'var(--cds-text-secondary)'}}>
            <span style={{fontWeight: 'bold'}}>Assignee:</span> From{' '}
            <Tag size="sm" type="gray">
              {previousAssignee}
            </Tag>
            to{' '}
            <Tag size="sm" type="gray">
              No assignee
            </Tag>
          </div>
        </div>
      );
    case 'UPDATE':
      if (details?.properties && details.properties.length > 0) {
        const properties = details.properties;
        return (
          <div>
            {properties.map((prop, index) => {
              const formattedOldValue = formatPropertyValue(
                prop.oldValue,
                prop.property,
              );
              const formattedNewValue =
                prop.newValue !== undefined
                  ? formatPropertyValue(prop.newValue, prop.property)
                  : null;

              const unsetText =
                prop.property === 'Due Date' ? 'No date' : 'Not set';
              const oldValueDisplay =
                prop.oldValue !== undefined ? formattedOldValue : unsetText;
              const newValueDisplay =
                formattedNewValue !== null ? formattedNewValue : unsetText;

              return (
                <div
                  key={prop.property}
                  style={{
                    marginBottom: index < properties.length - 1 ? '0.75rem' : 0,
                  }}
                >
                  <div style={{fontSize: '0.875rem', color: 'var(--cds-text-secondary)'}}>
                    <span style={{fontWeight: 'bold'}}>{prop.property}:</span>{' '}
                    From{' '}
                    <Tag size="sm" type="gray">
                      {oldValueDisplay}
                    </Tag>
                    to{' '}
                    <Tag size="sm" type="gray">
                      {newValueDisplay}
                    </Tag>
                  </div>
                </div>
              );
            })}
          </div>
        );
      }
      // Legacy: single property support
      const formattedOldValue = formatPropertyValue(
        details?.oldValue,
        details?.property,
      );
      const formattedNewValue =
        details?.newValue !== undefined
          ? formatPropertyValue(details?.newValue, details?.property)
          : null;

      const unsetText =
        details?.property === 'Due Date' ? 'No date' : 'Not set';
      const oldValueDisplay =
        details?.oldValue !== undefined ? formattedOldValue : unsetText;
      const newValueDisplay =
        formattedNewValue !== null ? formattedNewValue : unsetText;

      return (
        <div>
          {details?.property && (
            <div style={{fontSize: '0.875rem', color: 'var(--cds-text-secondary)'}}>
              <span style={{fontWeight: 'bold'}}>{details.property}:</span>{' '}
              From{' '}
              <Tag size="sm" type="gray">
                {oldValueDisplay}
              </Tag>
              to{' '}
              <Tag size="sm" type="gray">
                {newValueDisplay}
              </Tag>
            </div>
          )}
        </div>
      );
    default:
      return null;
  }
};

const History: React.FC = () => {
  const {task} = useOutletContext<OutletContext>();
  const {data: history, isLoading} = useTaskHistory(task.userTaskKey);
  const [sortBy, setSortBy] = useState<string>('timestamp');
  const [sortOrder, setSortOrder] = useState<'ASC' | 'DESC'>('DESC');

  const successfulHistory = useMemo(
    () => (history || []).filter((entry) => entry.status === 'SUCCESS'),
    [history],
  );

  const sortedHistory = useMemo(() => {
    const sorted = [...successfulHistory];
    sorted.sort((a, b) => {
      let aValue: string | number;
      let bValue: string | number;

      switch (sortBy) {
        case 'operationType':
          aValue = OPERATION_TYPE_LABELS[a.operationType] || a.operationType;
          bValue = OPERATION_TYPE_LABELS[b.operationType] || b.operationType;
          break;
        case 'actor':
          aValue = a.actor.name.toLowerCase();
          bValue = b.actor.name.toLowerCase();
          break;
        case 'timestamp':
        default:
          aValue = new Date(a.timestamp).getTime();
          bValue = new Date(b.timestamp).getTime();
          break;
      }

      if (aValue < bValue) {
        return sortOrder === 'ASC' ? -1 : 1;
      }
      if (aValue > bValue) {
        return sortOrder === 'ASC' ? 1 : -1;
      }
      return 0;
    });
    return sorted;
  }, [successfulHistory, sortBy, sortOrder]);

  const headers = [
    {key: 'operationType', header: 'Operation'},
    {key: 'details', header: 'Details'},
    {key: 'actor', header: 'Performed by'},
    {key: 'timestamp', header: 'Time'},
  ];

  const rows = useMemo(
    () =>
      sortedHistory.map((entry: TaskHistoryOperation) => ({
        id: entry.id,
        operationType:
          OPERATION_TYPE_LABELS[entry.operationType] || entry.operationType,
        details: formatDetailsSummary(entry),
        actor: entry.actor.name,
        timestamp: formatTaskCardRelativeDate(entry.timestamp),
      })),
    [sortedHistory],
  );

  // Create a map of row IDs to original entries for expanded content
  const entryMap = useMemo(
    () =>
      new Map(
        sortedHistory.map((entry) => [entry.id, entry])
      ),
    [sortedHistory],
  );

  const handleSort = (columnKey: string) => {
    if (sortBy === columnKey) {
      setSortOrder(sortOrder === 'ASC' ? 'DESC' : 'ASC');
    } else {
      setSortBy(columnKey);
      setSortOrder('ASC');
    }
  };

  if (isLoading) {
    return (
      <div className={styles.loadingSkeleton}>
        <SkeletonText paragraph lineCount={5} />
      </div>
    );
  }

  if (successfulHistory.length === 0) {
    return (
      <div className={styles.emptyState}>
        <p>No history available for this task</p>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <DataTable
        rows={rows}
        headers={headers}
        size="md"
        isSortable
      >
        {({
          rows,
          headers,
          getHeaderProps,
          getRowProps,
          getTableProps,
          getTableContainerProps,
        }: any) => (
          <TableContainer {...getTableContainerProps()}>
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  <TableExpandHeader aria-label="expand row" />
                  {headers.map((header: any) => {
                    const headerProps = getHeaderProps({header});
                    return (
                    <TableHeader
                        {...headerProps}
                      key={header.key}
                        isSortable={header.key !== 'details'}
                        isSortHeader={sortBy === header.key}
                        sortDirection={
                          sortBy === header.key
                            ? sortOrder === 'ASC'
                              ? 'ASC'
                              : 'DESC'
                            : 'NONE'
                        }
                        onClick={() => header.key !== 'details' && handleSort(header.key)}
                    >
                      {header.header}
                    </TableHeader>
                    );
                  })}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row: any) => {
                  const {key, ...rowProps} = getRowProps({row});
                  const entry = entryMap.get(row.id);
                  const expandedContent = entry ? formatDetailsExpanded(entry) : null;
                  const hasExpandedContent = expandedContent !== null;

                  if (hasExpandedContent) {
                    return (
                      <React.Fragment key={row.id}>
                        <TableExpandRow {...rowProps} isExpanded={row.isExpanded}>
                          {row.cells.map((cell: any) => (
                            <TableCell key={cell.id}>{cell.value}</TableCell>
                          ))}
                        </TableExpandRow>
                        <TableExpandedRow colSpan={headers.length + 1}>
                          {expandedContent}
                        </TableExpandedRow>
                      </React.Fragment>
                    );
                  }

                  // Non-expandable row (e.g., COMPLETE operations)
                  return (
                    <TableRow key={row.id} {...rowProps}>
                      {/* Empty cell for expand column alignment */}
                      <TableCell />
                      {row.cells.map((cell: any) => (
                        <TableCell key={cell.id}>{cell.value}</TableCell>
                      ))}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DataTable>
    </div>
  );
};

History.displayName = 'TaskHistory';

export {History as Component};

