/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState} from 'react';
import {
  ComposedModal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Stack,
  StructuredListWrapper,
  StructuredListCell,
  StructuredListBody,
  ActionableNotification,
} from '@carbon/react';
import {DataTable} from 'modules/components/DataTable';
import {formatDate} from 'modules/utils/date';
import type {MockAuditLogEntry} from './mocks';
import {
  Subtitle,
  FirstColumn,
  VerticallyAlignedRow,
  Title,
} from './styled';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {CheckmarkOutline} from '@carbon/react/icons';
import {EventSchedule, UserAvatar} from '@carbon/icons-react';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {StatusIndicator} from 'App/AuditLog/StatusIndicator';

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

const renderUserTaskDetails = (
  entry: MockAuditLogEntry,
): React.ReactNode | null => {
  if (!entry.details?.userTask) {
    return null;
  }

  const {assignee, oldAssignee, dueDate, oldDueDate} = entry.details.userTask;

  // Build table rows based on operation type
  const rows: Array<{
    id: string;
    property: string;
    new?: string;
    previous?: string;
  }> = [];

  if (entry.operationType === 'ASSIGN_USER_TASK') {
    rows.push({
      id: 'assignee',
      property: 'Assignee',
      new: assignee,
      previous: oldAssignee,
    });
  } else if (entry.operationType === 'UNASSIGN_USER_TASK') {
    rows.push({
      id: 'assignee',
      property: 'Assignee',
      previous: oldAssignee,
    });
  } else if (entry.operationType === 'UPDATE_USER_TASK') {
    rows.push({
      id: 'dueDate',
      property: 'Due date',
      new: dueDate ? formatDate(dueDate, '--', 'PPP') ?? undefined : undefined,
      previous: oldDueDate
        ? formatDate(oldDueDate, '--', 'PPP') ?? undefined
        : undefined,
    });
  }

  if (rows.length === 0) {
    return null;
  }

  // Build headers dynamically based on whether we have new/previous values
  const hasNew = rows.some((row) => row.new !== undefined);
  const hasPrevious = rows.some((row) => row.previous !== undefined);

  const headers = [
    {header: 'Property', key: 'property'},
    ...(hasNew ? [{header: 'New value', key: 'new'}] : []),
    ...(hasPrevious ? [{header: 'Previous value', key: 'previous'}] : []),
  ];

  return (
    <div>
      <Title>Details</Title>
      <DataTable headers={headers} rows={rows} />
    </div>
  );
};

type Props = {
  open: boolean;
  onClose: () => void;
  entry: MockAuditLogEntry | null;
};

const DetailsModal: React.FC<Props> = ({open, onClose, entry}) => {
  if (!entry) {
    return null;
  }

  const formatOperationType = (type: string) => {
    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const renderDetails = () => {
    let detailsContent: React.ReactNode = null;

    switch (entry.operationType) {
      case 'ADD_VARIABLE':
      case 'UPDATE_VARIABLE': {
        const variable = entry.details?.variable;
        detailsContent = variable ? (
          <DataTable
            isExpandable
            expandedContents={{
              [variable.name]: (
                <Suspense>
                  <JSONEditor
                    value={beautifyJSON(JSON.stringify(variable.newValue))}
                    readOnly
                    height="10vh"
                    width="95%"
                  />
                </Suspense>
              ),
            }}
            headers={[
              {header: 'Variable name', key: 'name'},
              {header: 'New value', key: 'newValue'},
            ]}
            rows={([
              {
                id: variable.name,
                name: variable.name,
                newValue: JSON.stringify(variable.newValue),
              },
            ] as any)}
          />
        ) : null;
        break;
      }
      default:
        detailsContent = null;
    }

    if (detailsContent === null) {
      return null;
    }

    return (
      <div>
        <Title>Details</Title>
        {detailsContent}
      </div>
    );
  };

  return (
    <ComposedModal size="md" open={open} onClose={onClose}>
      <ModalHeader
        title={formatOperationType(entry.operationType)}
        closeModal={onClose}
      />
      <ModalBody>
        <Stack gap={5}>
          <Stack gap={1}>
            <StructuredListWrapper isCondensed isFlush>
              <StructuredListBody>
                <VerticallyAlignedRow head>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <CheckmarkOutline />
                      Status
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    <StatusIndicator status={entry.operationState} />
                  </StructuredListCell>
                </VerticallyAlignedRow>
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <UserAvatar />
                      Applied by
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{entry.user}</StructuredListCell>
                </VerticallyAlignedRow>
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                  <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <EventSchedule />
                      Time
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    {formatDate(entry.startTimestamp)}
                  </StructuredListCell>
                </VerticallyAlignedRow>
              </StructuredListBody>
            </StructuredListWrapper>
          </Stack>
          {entry.operationState === 'fail' && entry.errorMessage && (
            <>
            <Stack gap={1}>
              <Subtitle>Error Message</Subtitle>
              <div>
                {entry.errorMessage}
              </div>
            </Stack>
            </>
          )}
          {entry.isMultiInstanceOperation && (
            <Stack gap={2}>
              <ActionableNotification
                kind="info"
                lowContrast
                inline
                hideCloseButton
                title="This operation is part of a batch"
                actionButtonLabel="View batch operation details"
              />
            </Stack>
          )}
          {renderDetails()}
          {renderUserTaskDetails(entry)}
        </Stack>
      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={onClose}>
          Close
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
};

export {DetailsModal};
