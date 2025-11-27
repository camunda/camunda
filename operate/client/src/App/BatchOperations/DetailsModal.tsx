/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useMemo} from 'react';
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
  InlineNotification,
  Link,
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableExpandRow,
  TableExpandedRow,
  TableExpandHeader,
  TableContainer,
  Pagination,
} from '@carbon/react';
import {
  CheckmarkOutline,
  EventSchedule,
  UserAvatar,
  Close,
} from '@carbon/icons-react';
import {formatDate} from 'modules/utils/date';
import type {BatchOperation} from 'modules/mocks/batchOperations';
import {BatchOperationStatusTag} from './BatchOperationStatusTag';

// Styled components
const VerticallyAlignedRow: React.FC<{
  children: React.ReactNode;
  head?: boolean;
}> = ({children, head, ...props}) => (
  <tr
    {...props}
    style={{
      borderBottom: head
        ? '1px solid var(--cds-border-strong)'
        : '1px solid var(--cds-border-subtle)',
    }}
  >
    {children}
  </tr>
);

const FirstColumn: React.FC<{
  children: React.ReactNode;
  noWrap?: boolean;
}> = ({children, noWrap, ...props}) => (
  <StructuredListCell
    {...props}
    head
    style={{
      fontWeight: 400,
      whiteSpace: noWrap ? 'nowrap' : 'normal',
      width: '180px',
    }}
  >
    {children}
  </StructuredListCell>
);

const Subtitle: React.FC<{children: React.ReactNode}> = ({children}) => (
  <h5 style={{fontWeight: 600, marginBottom: 'var(--cds-spacing-03)'}}>
    {children}
  </h5>
);

const formatOperationType = (type: string) => {
  return type
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
};

type Props = {
  open: boolean;
  onClose: () => void;
  operation: BatchOperation | null;
};

const DetailsModal: React.FC<Props> = ({open, onClose, operation}) => {
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const paginatedItems = useMemo(() => {
    if (!operation) {
      return [];
    }
    const startIndex = (currentPage - 1) * pageSize;
    return operation.items.slice(startIndex, startIndex + pageSize);
  }, [operation, currentPage, pageSize]);

  const itemsTableRows = useMemo(
    () =>
      paginatedItems.map((item) => ({
        id: item.id,
        instanceName: item.processDefinitionName,
        key: item.processInstanceKey,
        state: item.state,
        time: formatDate(item.timestamp),
        errorMessage: item.errorMessage,
      })),
    [paginatedItems],
  );

  const itemsTableHeaders = [
    {key: 'instanceName', header: 'Instance name'},
    {key: 'key', header: 'Key'},
    {key: 'state', header: 'State'},
    {key: 'time', header: 'Time'},
  ];

  if (!operation) {
    return null;
  }

  return (
    <ComposedModal size="lg" open={open} onClose={onClose}>
      <ModalHeader closeModal={onClose}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
            paddingRight: 'var(--cds-spacing-09)',
          }}
        >
          <h3>{formatOperationType(operation.operationType)}</h3>
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Close}
            iconDescription="Close"
            onClick={onClose}
          />
        </div>
      </ModalHeader>
      <ModalBody>
        <Stack gap={6}>
          {/* Summary Section */}
          <Stack gap={4}>
            <StructuredListWrapper isCondensed isFlush>
              <StructuredListBody>
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <CheckmarkOutline />
                      State
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    <BatchOperationStatusTag status={operation.state} />
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
                      <EventSchedule />
                      Start time
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    {formatDate(operation.startTime)}
                  </StructuredListCell>
                </VerticallyAlignedRow>
                {operation.endTime && (
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
                        End time
                      </div>
                    </FirstColumn>
                    <StructuredListCell>
                      {formatDate(operation.endTime)}
                    </StructuredListCell>
                  </VerticallyAlignedRow>
                )}
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
                  <StructuredListCell>{operation.appliedBy}</StructuredListCell>
                </VerticallyAlignedRow>
              </StructuredListBody>
            </StructuredListWrapper>

            {/* Error message for failed operations */}
            {operation.state === 'FAILED' && operation.errorMessage && (
              <InlineNotification
                kind="error"
                title="Failure reason:"
                subtitle={operation.errorMessage}
                hideCloseButton
                lowContrast
              />
            )}
          </Stack>

          {/* Items Section */}
          <Stack gap={3}>
            <Subtitle>
              {operation.totalItems} item{operation.totalItems !== 1 ? 's' : ''}
            </Subtitle>

            <DataTable
              rows={itemsTableRows}
              headers={itemsTableHeaders}
              render={({
                rows,
                headers,
                getTableProps,
                getTableContainerProps,
                getRowProps,
              }) => (
                <TableContainer {...getTableContainerProps()}>
                  <Table {...getTableProps()}>
                    <TableHead>
                      <TableRow>
                        <TableExpandHeader aria-label="expand row" />
                        {headers.map((header) => (
                          <TableHeader key={header.key}>
                            {header.header}
                          </TableHeader>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((row) => {
                        const {key, ...rowProps} = getRowProps({row});
                        const stateCell = row.cells.find(
                          (cell: any) => cell.info.header === 'state',
                        );
                        const hasError = stateCell?.value === 'FAILED';

                        return (
                          <React.Fragment key={key}>
                            <TableExpandRow
                              {...rowProps}
                              style={{
                                backgroundColor: hasError
                                  ? 'var(--cds-support-error-inverse)'
                                  : undefined,
                              }}
                            >
                              {row.cells.map((cell: any) => (
                                <TableCell key={cell.id}>
                                  {cell.info.header === 'state' ? (
                                    <BatchOperationStatusTag
                                      status={cell.value}
                                    />
                                  ) : cell.info.header === 'key' ? (
                                    <Link
                                      href="#"
                                      onClick={(e: React.MouseEvent) =>
                                        e.preventDefault()
                                      }
                                    >
                                      {cell.value}
                                    </Link>
                                  ) : (
                                    cell.value
                                  )}
                                </TableCell>
                              ))}
                            </TableExpandRow>
                            {hasError && (
                              <TableExpandedRow colSpan={headers.length + 1}>
                                <div
                                  style={{
                                    padding: 'var(--cds-spacing-03)',
                                    color: 'var(--cds-text-error)',
                                  }}
                                >
                                  {
                                    itemsTableRows.find((r) => r.id === row.id)
                                      ?.errorMessage
                                  }
                                </div>
                              </TableExpandedRow>
                            )}
                          </React.Fragment>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            />

            <Pagination
              page={currentPage}
              pageSize={pageSize}
              pageSizes={[10, 25, 50]}
              totalItems={operation.items.length}
              onChange={({page, pageSize: newPageSize}) => {
                setCurrentPage(page);
                setPageSize(newPageSize);
              }}
            />
          </Stack>
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

