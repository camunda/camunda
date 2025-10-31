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
  Tag,
  Dropdown,
} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {formatDate} from 'modules/utils/date';
import {DetailsModal} from './DetailsModal';
import {mockOperationLog} from './mocks';
import type {MockAuditLogEntry} from './mocks';
//import {useProcessInstancePageParams} from '../../../useProcessInstancePageParams';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {EmptyMessageContainer} from '../styled';

type DetailsModalState = {
  open: boolean;
  entry: MockAuditLogEntry | null;
};

const getOperationStateType = (
  state: string,
): 'red' | 'green' | 'blue' | 'gray' | 'purple' | 'cyan' => {
  switch (state) {
    case 'Completed':
      return 'green';
    case 'Failed':
    case 'Cancelled':
      return 'red';
    case 'Active':
      return 'blue';
    case 'Created':
      return 'cyan';
    default:
      return 'gray';
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

  const [sortKey, setSortKey] = useState<string>('startTimestamp');
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC' | 'NONE'>(
    'DESC',
  );

  const handleSort = (key: string) => {
    if (key === 'actions') {
      return;
    }
    if (key !== sortKey) {
      setSortKey(key);
      setSortDirection('ASC');
      return;
    }
    setSortDirection((prev) => (prev === 'ASC' ? 'DESC' : prev === 'DESC' ? 'NONE' : 'ASC'));
  };

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
    {key: 'operationState', header: 'Status'},
    {key: 'user', header: 'Performed by'},
    {key: 'startTimestamp', header: 'Time'},
    {key: 'actions', header: ' '},
  ];

  const rows = useMemo(
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
        // keep raw value for accurate sorting
        startTimestampRaw: entry.startTimestamp,
        actions: (
          <Stack orientation="horizontal" gap={2}>
            <button
              type="button"
              onClick={() => openDetailsModal(entry)}
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
        ),
      })) || [],
    [isRootNodeSelected, isUserTaskSelected],
  );

  const operationTypeOptions = useMemo(() => {
    const unique = Array.from(
      new Set(rows.map((row: any) => row.operationType)),
    );
    return ['All operations', ...unique];
  }, [rows]);

  const filteredRows = useMemo(() => {
    if (operationTypeFilter === 'All operations') {
      return rows;
    }
    return rows.filter((row) => row.operationType === operationTypeFilter);
  }, [rows, operationTypeFilter]);

  const sortedRows = useMemo(() => {
    if (sortDirection === 'NONE') {
      return filteredRows;
    }
    const data = [...filteredRows];
    const directionMultiplier = sortDirection === 'ASC' ? 1 : -1;

    data.sort((a: any, b: any) => {
      const key = sortKey;
      let aValue = a[key];
      let bValue = b[key];

      if (key === 'startTimestamp') {
        aValue = new Date(a.startTimestampRaw).getTime();
        bValue = new Date(b.startTimestampRaw).getTime();
      }

      if (aValue == null && bValue == null) return 0;
      if (aValue == null) return -1 * directionMultiplier;
      if (bValue == null) return 1 * directionMultiplier;

      if (typeof aValue === 'string' && typeof bValue === 'string') {
        return aValue.localeCompare(bValue) * directionMultiplier;
      }

      if (aValue < bValue) return -1 * directionMultiplier;
      if (aValue > bValue) return 1 * directionMultiplier;
      return 0;
    });

    return data;
  }, [filteredRows, sortDirection, sortKey]);

  if (!isRootNodeSelected && !isUserTaskSelected) {
    return (
      <EmptyMessageContainer>
        <EmptyMessage message="This element has no operations" />
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
        <Table size="md" style={{tableLayout: 'fixed', width: '100%'}}>
          <TableHead>
            <TableRow>
              {headers.map((header) => (
                <TableHeader
                  key={header.key}
                  isSortable={header.key !== 'actions'}
                  isSortHeader={sortKey === header.key}
                  sortDirection={sortKey === header.key ? sortDirection : 'NONE'}
                  onClick={() => handleSort(header.key)}
                  style={header.key === 'actions' ? {width: '72px'} : {}}
                >
                  {header.header}
                </TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedRows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>{row.operationType}</TableCell>
                <TableCell>
                  <Tag
                    size="sm"
                    type={getOperationStateType(row.operationState)}
                  >
                    {row.operationState}
                  </Tag>
                </TableCell>
                <TableCell>{row.user}</TableCell>
                <TableCell>{row.startTimestamp}</TableCell>
                <TableCell>{row.actions}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
