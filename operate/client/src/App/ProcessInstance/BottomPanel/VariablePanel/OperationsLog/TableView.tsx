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
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  Dropdown,
} from '@carbon/react';
import {Information, CheckmarkFilled, ErrorFilled} from '@carbon/react/icons';
import {formatDate} from 'modules/utils/date';
import {DetailsModal} from './DetailsModal';
import {mockOperationLog} from './mocks';
import type {MockAuditLogEntry} from './mocks';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {EmptyMessageContainer} from '../styled';

type DetailsModalState = {
  open: boolean;
  entry: MockAuditLogEntry | null;
};

const getOperationStateIcon = (state: string) => {
  switch (state) {
    case 'success':
      return (
        <CheckmarkFilled
          size={16}
          style={{color: 'var(--cds-support-success)'}}
        />
      );
    case 'fail':
      return (
        <ErrorFilled
          size={16}
          style={{color: 'var(--cds-support-error)'}}
        />
      );
    default:
      return (
        <CheckmarkFilled
          size={16}
          style={{color: 'var(--cds-support-success)'}}
        />
      );
  }
};

const OperationsLogTable: React.FC = observer(() => {
  // const {processInstanceId = ''} = useProcessInstancePageParams();

  const isRootNodeSelected = useIsRootNodeSelected();
  const {data: businessObjects} = useBusinessObjects();
  const selectedFlowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const isUserTaskSelected =
    !isRootNodeSelected &&
    selectedFlowNodeId !== undefined &&
    businessObjects?.[selectedFlowNodeId]?.$type === 'bpmn:UserTask';

  const [detailsModal, setDetailsModal] = useState<DetailsModalState>({
    open: false,
    entry: null,
  });

  const [operationTypeFilter, setOperationTypeFilter] = useState<string>(
    'All operations',
  );


  const openDetailsModal = (entry: MockAuditLogEntry) => {
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

  const formatOperationState = (state: string) => {
    return state
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const headers = [
    {key: 'operationType', header: 'Operation'},
    {key: 'user', header: 'Applied by'},
    {key: 'startTimestamp', header: 'Time'},
    {key: 'actions', header: ' '},
  ];

  const baseRows = useMemo(
    () =>
      (
        isRootNodeSelected
          ? mockOperationLog.filter(
              (entry: MockAuditLogEntry) => entry.details?.userTask === undefined,
            )
          : isUserTaskSelected
          ? mockOperationLog.filter(
              (entry: MockAuditLogEntry) => entry.details?.userTask !== undefined,
            )
          : []
      ).map((entry: MockAuditLogEntry) => ({
        id: entry.id,
        operationType: formatOperationType(entry.operationType),
        operationState: formatOperationState(entry.operationState),
        user: entry.user,
        startTimestamp: formatDate(entry.startTimestamp),
        startTimestampRaw: entry.startTimestamp,
        entry: entry,
      })) || [],
    [isRootNodeSelected, isUserTaskSelected],
  );

  const operationTypeOptions = useMemo(() => {
    const unique = Array.from(
      new Set(baseRows.map((row: any) => row.operationType)),
    );
    return ['All operations', ...unique];
  }, [baseRows]);

  const filteredRows = useMemo(() => {
    if (operationTypeFilter === 'All operations') {
      return baseRows;
    }
    return baseRows.filter((row) => row.operationType === operationTypeFilter);
  }, [baseRows, operationTypeFilter]);

  if (!isRootNodeSelected && !isUserTaskSelected) {
    return (
      <EmptyMessageContainer>
        <EmptyMessage message="The element has no operations" />
      </EmptyMessageContainer>
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
        <div style={{padding: 'var(--cds-spacing-03) var(--cds-spacing-05)'}}>
          <Dropdown
            id="operationTypeFilter"
            data-testid="operation-type-filter"
            titleText="Operation type"
            label="All operations"
            hideLabel
            items={operationTypeOptions}
            size="md"
            selectedItem={operationTypeFilter}
            style={{width: 232}}
            onChange={({selectedItem}) => {
              if (typeof selectedItem === 'string') {
                setOperationTypeFilter(selectedItem);
              }
            }}
          />
        </div>
        <DataTable
          rows={filteredRows}
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
            <Table {...getTableProps()} style={{tableLayout: 'fixed', width: '100%'}}>
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
                  const rowData = filteredRows.find((r) => r.id === row.id);
                  
                  return (
                    <TableRow {...rowProps} key={key}>
                      {row.cells.map((cell) => {
                        if (cell.info.header === 'operationType') {
                          return (
                            <TableCell key={cell.id}>
                              <div
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 'var(--cds-spacing-03)',
                                }}
                              >
                                {rowData && getOperationStateIcon(rowData.entry.operationState)}
                                <span>{cell.value}</span>
                              </div>
                            </TableCell>
                          );
                        }
                        if (cell.info.header === 'actions') {
                          return (
                            <TableCell key={cell.id}>
                              <Stack orientation="horizontal" gap={2}>
                                <button
                                  type="button"
                                  onClick={() => rowData && openDetailsModal(rowData.entry)}
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
});

export {OperationsLogTable};
