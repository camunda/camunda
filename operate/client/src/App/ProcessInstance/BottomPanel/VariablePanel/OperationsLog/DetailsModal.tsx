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
  Tag,
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
import {
  EventSchedule,
  UserAvatar,
  Calendar,
  Launch,
} from '@carbon/icons-react';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';

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
  onClose: () => void,
): React.ReactNode | null => {
  if (!entry.details?.userTask) {
    return null;
  }

  const {assignee, oldAssignee, dueDate, oldDueDate, name, elementId} =
    entry.details.userTask;

  // Check if there's a user task name to show
  if (!name) {
    return null;
  }

  return (
    <div>
      <Title>Details</Title>
      <StructuredListWrapper isCondensed isFlush>
        <StructuredListBody>
          {/* User task reference - always shown */}
          <VerticallyAlignedRow head>
            <FirstColumn noWrap>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--cds-spacing-03)',
                }}
              >
                User task instance
              </div>
            </FirstColumn>
            <StructuredListCell>
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 'var(--cds-spacing-02)',
                  cursor: 'pointer',
                }}
                onClick={() => {
                  if (elementId) {
                    instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                      elementId,
                    );
                  }
                  onClose();
                }}
              >
                <span>{name}</span>
                <Launch size={14} />
              </div>
            </StructuredListCell>
          </VerticallyAlignedRow>
          {/* Assign user task: show new assignee and previous assignee */}
          {entry.operationType === 'ASSIGN_USER_TASK' && (
            <>
              {oldAssignee && (
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      Previous assignee
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{oldAssignee}</StructuredListCell>
                </VerticallyAlignedRow>
              )}
              {assignee && (
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      New assignee
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{assignee}</StructuredListCell>
                </VerticallyAlignedRow>
              )}
            </>
          )}
          {/* Unassign user task: show previous assignee only */}
          {entry.operationType === 'UNASSIGN_USER_TASK' && oldAssignee && (
            <VerticallyAlignedRow>
              <FirstColumn noWrap>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-03)',
                  }}
                >
                  Previous assignee
                </div>
              </FirstColumn>
              <StructuredListCell>{oldAssignee}</StructuredListCell>
            </VerticallyAlignedRow>
          )}
          {/* Update user task: show property with old and new values */}
          {entry.operationType === 'UPDATE_USER_TASK' && (
            <>
              {oldDueDate && (
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <Calendar />
                      Previous due date
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{formatDate(oldDueDate, '--', 'PPP')}</StructuredListCell>
                </VerticallyAlignedRow>
              )}
              {dueDate && (
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <Calendar />
                      New due date
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{formatDate(dueDate, '--', 'PPP')}</StructuredListCell>
                </VerticallyAlignedRow>
              )}
            </>
          )}
        </StructuredListBody>
      </StructuredListWrapper>
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

  const formatOperationState = (state: string) => {
    switch (state) {
      case 'success':
        return 'Success';
      case 'fail':
        return 'Failed';
      default:
        return state
          .split('_')
          .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
          .join(' ');
    }
  };

  const getOperationStateType = (
    state: string,
  ): 'green' | 'red' => {
    switch (state) {
      case 'success':
        return 'green';
      case 'fail':
        return 'red';
      default:
        return 'green';
    }
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
              {header: 'Element scope', key: 'scope'},
            ]}
            rows={([
              {
                id: variable.name,
                name: variable.name,
                newValue: JSON.stringify(variable.newValue),
                scope: (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 'var(--cds-spacing-02)',
                      cursor: 'pointer',
                    }}
                    onClick={() => {
                      if (variable.scope) {
                        instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                          variable.scope.id,
                        );
                      }
                      onClose();
                    }}
                  >
                    {variable.scope?.name ?? ''}
                    <Launch size={14} />
                  </div>
                ),
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
        label="Operation Details"
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
                    <Tag
                      type={getOperationStateType(entry.operationState)}
                      size="md"
                    >
                      {formatOperationState(entry.operationState)}
                    </Tag>
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
          {renderUserTaskDetails(entry, onClose)}
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
