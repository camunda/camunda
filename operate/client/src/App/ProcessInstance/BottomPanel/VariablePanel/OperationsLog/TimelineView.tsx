/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo} from 'react';
import {observer} from 'mobx-react';
import {Tag} from '@carbon/react';
import {Information, Edit, Add} from '@carbon/react/icons';
import {formatDate} from 'modules/utils/date';
import {useAuditLog} from 'modules/queries/auditLog/useAuditLog';
import type {
  AuditLogEntry,
  AuditLogSearchRequest,
} from 'modules/api/v2/auditLog/searchAuditLog';
import {CommentModal} from 'App/AuditLog/CommentModal';
import {
  TimelineContainer,
  TimelineItem,
  TimelineMarker,
  TimelineContent,
  TimelineHeader,
  TimelineTitle,
  TimelineMetadata,
  TimelineBody,
  TimelineActions,
  TimelineLine,
} from './styled';

type CommentModalState = {
  open: boolean;
  entryId: string;
  initialComment?: string;
  mode: 'view' | 'edit' | 'add';
};

const OperationsLogTimeline: React.FC = observer(() => {
  const [commentModal, setCommentModal] = useState<CommentModalState>({
    open: false,
    entryId: '',
    mode: 'view',
  });

  // Build request to fetch operations for this process instance
  const request: AuditLogSearchRequest = useMemo(
    () => ({
      sort: [
        {
          field: 'startTimestamp',
          order: 'DESC',
        },
      ],
      filter: {},
      page: {
        from: 0,
        limit: 50,
      },
    }),
    [],
  );

  const {data, isLoading, error} = useAuditLog(request);

  const openCommentModal = (
    entryId: string,
    mode: 'view' | 'edit' | 'add',
    initialComment?: string,
  ) => {
    setCommentModal({open: true, entryId, mode, initialComment});
  };

  const closeCommentModal = () => {
    setCommentModal({open: false, entryId: '', mode: 'view'});
  };

  const formatOperationType = (type: string) => {
    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const getOperationStateType = (
    state: string,
  ): 'red' | 'green' | 'blue' | 'gray' | 'purple' | 'cyan' => {
    switch (state) {
      case 'COMPLETED':
        return 'green';
      case 'FAILED':
      case 'CANCELLED':
        return 'red';
      case 'ACTIVE':
        return 'blue';
      case 'CREATED':
        return 'cyan';
      default:
        return 'gray';
    }
  };

  const formatOperationState = (state: string) => {
    return state
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  if (isLoading) {
    return <div style={{padding: '16px'}}>Loading operations...</div>;
  }

  if (error) {
    return (
      <div style={{padding: '16px', color: 'red'}}>
        Error loading operations log
      </div>
    );
  }

  if (!data?.items || data.items.length === 0) {
    return (
      <div style={{padding: '16px'}}>No operations found for this instance</div>
    );
  }

  return (
    <>
      <TimelineContainer>
        {data.items.map((entry: AuditLogEntry, index: number) => (
          <TimelineItem key={entry.id}>
            <TimelineMarker $state={entry.operationState} />
            {index < data.items.length - 1 && <TimelineLine />}
            <TimelineContent>
              <TimelineHeader>
                <TimelineTitle>
                  {formatOperationType(entry.operationType)}
                </TimelineTitle>
                <Tag
                  size="sm"
                  type={getOperationStateType(entry.operationState)}
                >
                  {formatOperationState(entry.operationState)}
                </Tag>
              </TimelineHeader>

              <TimelineMetadata>
                <span>{formatDate(entry.startTimestamp)}</span>
                <span>•</span>
                <span>{entry.user}</span>
              </TimelineMetadata>

              {entry.comment && <TimelineBody>{entry.comment}</TimelineBody>}

              <TimelineActions orientation="horizontal" gap={2}>
                {entry.comment && (
                  <>
                    <button
                      type="button"
                      onClick={() =>
                        openCommentModal(entry.id, 'view', entry.comment)
                      }
                      title="View comment"
                      style={{
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        padding: '4px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                        color: 'var(--cds-link-primary)',
                      }}
                    >
                      <Information size={16} />
                      <span style={{fontSize: '0.875rem'}}>View comment</span>
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        openCommentModal(entry.id, 'edit', entry.comment)
                      }
                      title="Edit comment"
                      style={{
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        padding: '4px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                        color: 'var(--cds-link-primary)',
                      }}
                    >
                      <Edit size={16} />
                      <span style={{fontSize: '0.875rem'}}>Edit</span>
                    </button>
                  </>
                )}
                {!entry.comment && (
                  <button
                    type="button"
                    onClick={() => openCommentModal(entry.id, 'add')}
                    title="Add comment"
                    style={{
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      padding: '4px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      color: 'var(--cds-link-primary)',
                    }}
                  >
                    <Add size={16} />
                    <span style={{fontSize: '0.875rem'}}>Add comment</span>
                  </button>
                )}
              </TimelineActions>
            </TimelineContent>
          </TimelineItem>
        ))}
      </TimelineContainer>

      <CommentModal
        open={commentModal.open}
        onClose={closeCommentModal}
        entryId={commentModal.entryId}
        initialComment={commentModal.initialComment}
        mode={commentModal.mode}
      />
    </>
  );
});

export {OperationsLogTimeline};
