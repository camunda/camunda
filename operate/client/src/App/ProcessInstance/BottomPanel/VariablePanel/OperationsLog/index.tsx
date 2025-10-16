/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo} from 'react';
import {observer} from 'mobx-react';
import {
  Stack,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
} from '@carbon/react';
import {Information, Edit, Add} from '@carbon/react/icons';
import {formatDate} from 'modules/utils/date';
import {useAuditLog} from 'modules/queries/auditLog/useAuditLog';
import type {
  AuditLogEntry,
  AuditLogSearchRequest,
} from 'modules/api/v2/auditLog/searchAuditLog';
import {CommentModal} from 'App/AuditLog/CommentModal';
//import {useProcessInstancePageParams} from '../../../useProcessInstancePageParams';

type CommentModalState = {
  open: boolean;
  entryId: string;
  initialComment?: string;
  mode: 'view' | 'edit' | 'add';
};

const OperationsLog: React.FC = observer(() => {
  // const {processInstanceId = ''} = useProcessInstancePageParams();

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
      filter: {
        // processInstanceKey: processInstanceId, disabling for demo purposes
      },
      page: {
        from: 0,
        limit: 50,
      },
    }),
    [],
    // [processInstanceId],
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

  const formatOperationState = (state: string) => {
    return state
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const headers = [
    {key: 'operationType', header: 'Operation'},
    {key: 'operationState', header: 'Status'},
    {key: 'user', header: 'User'},
    {key: 'startTimestamp', header: 'Timestamp'},
    {key: 'comment', header: 'Comment'},
    {key: 'actions', header: ''},
  ];

  const rows = useMemo(
    () =>
      data?.items.map((entry: AuditLogEntry) => ({
        id: entry.id,
        operationType: formatOperationType(entry.operationType),
        operationState: formatOperationState(entry.operationState),
        user: entry.user,
        startTimestamp: formatDate(entry.startTimestamp),
        comment: entry.comment ? (
          <div
            style={{
              maxWidth: '200px',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {entry.comment}
          </div>
        ) : (
          '-'
        ),
        actions: (
          <Stack orientation="horizontal" gap={2}>
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
                  }}
                >
                  <Information size={16} />
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
                  }}
                >
                  <Edit size={16} />
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
                }}
              >
                <Add size={16} />
              </button>
            )}
          </Stack>
        ),
      })) || [],
    [data],
  );

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
      <div
        style={{
          height: '100%',
          overflow: 'auto',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Table size="sm">
          <TableHead>
            <TableRow>
              {headers.map((header) => (
                <TableHeader key={header.key}>{header.header}</TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>{row.operationType}</TableCell>
                <TableCell>{row.operationState}</TableCell>
                <TableCell>{row.user}</TableCell>
                <TableCell>{row.startTimestamp}</TableCell>
                <TableCell>{row.comment}</TableCell>
                <TableCell>{row.actions}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

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

export {OperationsLog};
