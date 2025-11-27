/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo, useEffect} from 'react';
import {useParams, useNavigate, useLocation} from 'react-router-dom';
import React from 'react';
import {
  Button,
  Stack,
  InlineNotification,
  Link,
  Pagination,
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
} from '@carbon/react';
import {
  ArrowLeft,
  CheckmarkFilled,
  ErrorFilled,
  InProgress,
  SkipForwardFilled,
  StopFilledAlt,
} from '@carbon/icons-react';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {PAGE_TITLE} from 'modules/constants';
import {
  mockBatchOperations,
  type BatchOperationItemState,
} from 'modules/mocks/batchOperations';
import {BatchOperationStatusTag} from './BatchOperationStatusTag';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Paths} from 'modules/Routes';

// Item Status Indicator component for batch operation items
const ItemStatusIndicator: React.FC<{status: BatchOperationItemState}> = ({
  status,
}) => {
  const getStatusConfig = () => {
    switch (status) {
      case 'COMPLETED':
        return {
          Icon: CheckmarkFilled,
          color: 'var(--cds-support-success)',
          text: 'Completed',
        };
      case 'FAILED':
        return {
          Icon: ErrorFilled,
          color: 'var(--cds-support-error)',
          text: 'Failed',
        };
      case 'ACTIVE':
        return {
          Icon: InProgress,
          color: 'var(--cds-support-info)',
          text: 'Active',
        };
      case 'SKIPPED':
        return {
          Icon: SkipForwardFilled,
          color: 'var(--cds-text-secondary)',
          text: 'Skipped',
        };
      case 'CANCELLED':
        return {
          Icon: StopFilledAlt,
          color: 'var(--cds-support-warning)',
          text: 'Cancelled',
        };
      default:
        return {
          Icon: ErrorFilled,
          color: 'var(--cds-text-secondary)',
          text: status,
        };
    }
  };

  const {Icon, color, text} = getStatusConfig();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--cds-spacing-03)',
      }}
    >
      <Icon size={16} style={{color}} />
      <span>{text}</span>
    </div>
  );
};

// Reusable Summary Tile component
const SummaryTile: React.FC<{
  label: string;
  children: React.ReactNode;
}> = ({label, children}) => (
  <div
    style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--cds-spacing-03)',
      alignItems: 'flex-start',
      padding: 'var(--cds-spacing-05)',
      backgroundColor: 'var(--cds-layer-01)',
      border: '1px solid var(--cds-border-subtle)',
      minWidth: '200px',
      fontSize: 'var(--cds-font-size-03)',
    }}
  >
    <span
      style={{
        fontSize: '0.75rem',
        color: 'var(--cds-text-secondary)',
      }}
    >
      {label}
    </span>
    {children}
  </div>
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

const BatchOperationDetails: React.FC = () => {
  const {operationId} = useParams<{operationId: string}>();
  const navigate = useNavigate();
  const location = useLocation();
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Get sort from URL or use defaults
  const sortParams = getSortParams(location.search);
  const sortBy = sortParams?.sortBy || 'time';
  const sortOrder = sortParams?.sortOrder?.toUpperCase() || 'DESC';

  // Find the operation from mock data
  const operation = useMemo(() => {
    return mockBatchOperations.find((op) => op.id === operationId) || null;
  }, [operationId]);

  useEffect(() => {
    if (operation) {
      document.title = `${PAGE_TITLE.BATCH_OPERATIONS} - ${formatOperationType(operation.operationType)}`;
    } else {
      document.title = PAGE_TITLE.BATCH_OPERATIONS;
    }
  }, [operation]);

  // Sort and paginate items
  const sortedAndPaginatedItems = useMemo(() => {
    if (!operation) {
      return [];
    }

    // First sort the items
    const sortedItems = [...operation.items].sort((a, b) => {
      let comparison = 0;
      switch (sortBy) {
        case 'instanceName':
          comparison = a.processDefinitionName.localeCompare(
            b.processDefinitionName,
          );
          break;
        case 'key':
          comparison = a.processInstanceKey.localeCompare(b.processInstanceKey);
          break;
        case 'state':
          comparison = a.state.localeCompare(b.state);
          break;
        case 'time':
          comparison =
            new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();
          break;
        default:
          return 0;
      }
      return sortOrder === 'DESC' ? comparison : -comparison;
    });

    // Then paginate
    const startIndex = (currentPage - 1) * pageSize;
    return sortedItems.slice(startIndex, startIndex + pageSize);
  }, [operation, sortBy, sortOrder, currentPage, pageSize]);

  const itemsTableRows = useMemo(
    () =>
      sortedAndPaginatedItems.map((item) => ({
        id: item.id,
        instanceName: item.processDefinitionName,
        instanceKey: item.processInstanceKey,
        state: item.state,
        time: formatDate(item.timestamp),
        errorMessage: item.errorMessage,
      })),
    [sortedAndPaginatedItems],
  );

  const itemsTableHeaders = [
    {key: 'instanceName', header: 'Process name'},
    {key: 'instanceKey', header: 'Instance key'},
    {key: 'state', header: 'State'},
    {key: 'time', header: 'Time'},
  ];

  // Handle sort click
  const handleSort = (headerKey: string) => {
    const newParams = new URLSearchParams(location.search);
    const currentSort = getSortParams(location.search);

    if (headerKey === currentSort?.sortBy) {
      const newOrder = currentSort.sortOrder === 'asc' ? 'desc' : 'asc';
      newParams.set('sort', `${headerKey}+${newOrder}`);
    } else {
      newParams.set('sort', `${headerKey}+desc`);
    }

    navigate({search: newParams.toString()}, {replace: true});
  };

  if (!operation) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          backgroundColor: 'var(--cds-layer)',
          padding: 'var(--cds-spacing-05)',
        }}
      >
        <Link
          href="#"
          onClick={(e: React.MouseEvent) => {
            e.preventDefault();
            navigate(Paths.batchOperations());
          }}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--cds-spacing-02)',
            marginBottom: 'var(--cds-spacing-05)',
          }}
        >
          <ArrowLeft size={16} />
          Go back
        </Link>
        <InlineNotification
          kind="error"
          title="Operation not found"
          subtitle={`The batch operation with ID "${operationId}" could not be found.`}
          hideCloseButton
          lowContrast
        />
      </div>
    );
  }

  return (
    <>
      <VisuallyHiddenH1>
        {formatOperationType(operation.operationType)}
      </VisuallyHiddenH1>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          backgroundColor: 'var(--cds-layer)',
          overflow: 'hidden',
        }}
      >
        {/* Header with back link */}
        <div
          style={{
            padding: 'var(--cds-spacing-05)',
            paddingBottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Link
            href="#"
            onClick={(e: React.MouseEvent) => {
              e.preventDefault();
              navigate(Paths.batchOperations());
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--cds-spacing-02)',
            }}
          >
            <ArrowLeft size={16} />
            Go back
          </Link>
          <Button
            kind="ghost"
            size="sm"
            onClick={() => navigate(Paths.batchOperations())}
          >
            Close
          </Button>
        </div>

        {/* Title */}
        <div style={{padding: 'var(--cds-spacing-05)', paddingBottom: 0}}>
          <h3 style={{marginBottom: 'var(--cds-spacing-05)'}}>
            {formatOperationType(operation.operationType)}
          </h3>
        </div>

        {/* Content */}
        <div
          style={{
            flex: 1,
            overflow: 'auto',
            padding: 'var(--cds-spacing-05)',
          }}
        >
          <Stack gap={6}>
            {/* Summary Section - Separate tiles in one row */}
            <Stack gap={4}>
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 'var(--cds-spacing-04)',
                }}
              >
                <SummaryTile label="State">
                  <BatchOperationStatusTag status={operation.state} />
                </SummaryTile>

                <SummaryTile label="Start time">
                  <span style={{fontWeight: 500}}>
                    {formatDate(operation.startTime)}
                  </span>
                </SummaryTile>

                <SummaryTile label="End time">
                  <span style={{fontWeight: 500}}>
                    {operation.endTime ? formatDate(operation.endTime) : '-'}
                  </span>
                </SummaryTile>

                <SummaryTile label="Applied by">
                  <span style={{fontWeight: 500}}>{operation.appliedBy}</span>
                </SummaryTile>
              </div>

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
            <Stack gap={2}>
              <Subtitle>
                {operation.totalItems} instance
                {operation.totalItems !== 1 ? 's' : ''}
              </Subtitle>

              <DataTable
                rows={itemsTableRows}
                headers={itemsTableHeaders}
                isSortable
                render={({
                  rows,
                  headers,
                  getTableProps,
                  getHeaderProps,
                  getRowProps,
                  getTableContainerProps,
                }) => (
                  <TableContainer {...getTableContainerProps()}>
                    <Table {...getTableProps()} size="lg">
                      <TableHead>
                        <TableRow>
                          <TableExpandHeader aria-label="expand row" />
                          {headers.map((header) => {
                            const headerProps = getHeaderProps({header});
                            return (
                              <TableHeader
                                key={header.key}
                                isSortable
                                isSortHeader={sortBy === header.key}
                                sortDirection={
                                  sortBy === header.key
                                    ? sortOrder === 'ASC'
                                      ? 'ASC'
                                      : 'DESC'
                                    : 'NONE'
                                }
                                onClick={() => handleSort(header.key)}
                              >
                                {header.header}
                              </TableHeader>
                            );
                          })}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((row) => {
                          const rowData = itemsTableRows.find(
                            (r) => r.id === row.id,
                          );
                          const hasError = rowData?.state === 'FAILED';
                          const {key, ...rowProps} = getRowProps({row});

                          const renderCells = () =>
                            row.cells.map((cell: any) => (
                              <TableCell key={cell.id}>
                                {cell.info.header === 'state' ? (
                                  <ItemStatusIndicator status={cell.value} />
                                ) : cell.info.header === 'instanceKey' ? (
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
                            ));

                          // Only show expandable row for failed items
                          if (hasError) {
                            return (
                              <React.Fragment key={key}>
                                <TableExpandRow {...rowProps}>
                                  {renderCells()}
                                </TableExpandRow>
                                <TableExpandedRow colSpan={headers.length + 1}>
                                  <div
                                    style={{
                                      padding: 'var(--cds-spacing-04)',
                                    }}
                                  >
                                    <strong>Failure reason: </strong>
                                    {rowData?.errorMessage || 'Operation failed'}
                                  </div>
                                </TableExpandedRow>
                              </React.Fragment>
                            );
                          }

                          // Regular row for non-failed items
                          return (
                            <TableRow key={key} {...rowProps}>
                              {/* Empty cell for expand column alignment */}
                              <TableCell />
                              {renderCells()}
                            </TableRow>
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
        </div>
      </div>
    </>
  );
};

export {BatchOperationDetails};

