/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo} from 'react';
import {
  Stack,
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {formatDate} from 'modules/utils/date';
import {DetailsModal} from './DetailsModal';
import {mockDecisionOperationLog} from './mocks';
import type {MockDecisionAuditLogEntry} from './mocks';
import {StatusIndicator} from 'App/AuditLog/StatusIndicator';

type DetailsModalState = {
  open: boolean;
  entry: MockDecisionAuditLogEntry | null;
};

const OperationsLog: React.FC = () => {
  const [detailsModal, setDetailsModal] = useState<DetailsModalState>({
    open: false,
    entry: null,
  });

  const openDetailsModal = (entry: MockDecisionAuditLogEntry) => {
    setDetailsModal({open: true, entry});
  };

  const closeDetailsModal = () => {
    setDetailsModal({open: false, entry: null});
  };

  const formatOperationType = (type: string) => {
    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const headers = [
    {key: 'operationType', header: 'Operation'},
    {key: 'operationState', header: 'Status'},
    {key: 'user', header: 'Actor'},
    {key: 'startTimestamp', header: 'Time'},
    {key: 'actions', header: ' '},
  ];

  const rows = useMemo(
    () =>
      mockDecisionOperationLog.map((entry: MockDecisionAuditLogEntry) => ({
        id: entry.id,
        operationType: formatOperationType(entry.operationType),
        operationState: entry.operationState,
        user: entry.user,
        startTimestamp: formatDate(entry.startTimestamp),
        entry: entry,
      })),
    [],
  );

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
        <DataTable
          rows={rows}
          headers={headers}
          isSortable
          size="md"
          render={({
            rows,
            headers,
            getTableProps,
            getHeaderProps,
            getRowProps,
          }) => (
            <Table
              {...getTableProps()}
              style={{tableLayout: 'fixed', width: '100%'}}
            >
              <TableHead>
                <TableRow>
                  {headers.map((header) => {
                    const {key, ...headerProps} = getHeaderProps({
                      header,
                      isSortable: header.key !== 'actions',
                    });
                    return (
                      <TableHeader
                        {...headerProps}
                        key={key}
                        style={header.key === 'actions' ? {width: '72px'} : {}}
                      >
                        {header.header}
                      </TableHeader>
                    );
                  })}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => {
                  const {key, ...rowProps} = getRowProps({row});
                  const rowData = mockDecisionOperationLog.find(
                    (r) => r.id === row.id,
                  );

                  return (
                    <TableRow {...rowProps} key={key}>
                      {row.cells.map((cell) => {
                        if (cell.info.header === 'operationState') {
                          return (
                            <TableCell key={cell.id}>
                              {rowData && (
                                <StatusIndicator status={rowData.operationState} />
                              )}
                            </TableCell>
                          );
                        }
                        if (cell.info.header === 'actions') {
                          return (
                            <TableCell key={cell.id}>
                              <Stack orientation="horizontal" gap={2}>
                                <button
                                  type="button"
                                  onClick={() =>
                                    rowData && openDetailsModal(rowData)
                                  }
                                  title="View details"
                                  style={{
                                    background: 'none',
                                    border: 'none',
                                    cursor: 'pointer',
                                    padding: '4px',
                                    color: 'var(--cds-text-primary)',
                                  }}
                                >
                                  <Information size={16} />
                                </button>
                              </Stack>
                            </TableCell>
                          );
                        }
                        return <TableCell key={cell.id}>{cell.value}</TableCell>;
                      })}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        />
      </div>

      <DetailsModal
        open={detailsModal.open}
        onClose={closeDetailsModal}
        entry={detailsModal.entry}
      />
    </>
  );
};

export {OperationsLog};

