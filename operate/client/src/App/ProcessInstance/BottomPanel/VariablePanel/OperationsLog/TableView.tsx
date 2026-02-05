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
  CodeSnippet,
  Tooltip,
} from '@carbon/react';
import {Information, CheckmarkOutline, Error} from '@carbon/react/icons';
import {User, Api, Bot} from '@carbon/icons-react';
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

  // Convert display name to username format (e.g., "Michael Scott" -> "michael-scott")
  // If already in username format (no spaces, lowercase), return as is
  const formatUsername = (user: string): string => {
    if (!user) {
      return user;
    }
    
    // If it contains spaces or capital letters, it's likely a display name
    // Convert to username format: lowercase and replace spaces with hyphens
    if (/\s/.test(user) || /[A-Z]/.test(user)) {
      return user
        .toLowerCase()
        .trim()
        .replace(/\s+/g, '-') // Replace spaces with hyphens
        .replace(/[^a-z0-9-]/g, ''); // Remove special characters except hyphens
    }
    
    // Already in username format, return as is
    return user;
  };

  // Determine actor type and return appropriate icon
  const getActorType = (user: string): 'user' | 'client' | 'agent' => {
    if (!user) return 'user';
    
    const lowerUser = user.toLowerCase();
    
    // Check for AI agent indicators
    if (
      lowerUser.includes('ai') ||
      lowerUser.includes('agent') ||
      lowerUser.includes('bot') ||
      lowerUser.includes('robot') ||
      lowerUser.includes('assistant')
    ) {
      return 'agent';
    }
    
    // Check for API client indicators
    if (
      lowerUser.includes('api') ||
      lowerUser.includes('client') ||
      lowerUser.includes('service') ||
      lowerUser.includes('-client') ||
      lowerUser.endsWith('client')
    ) {
      return 'client';
    }
    
    // Default to user
    return 'user';
  };

  const getActorIcon = (actorType: 'user' | 'client' | 'agent') => {
    switch (actorType) {
      case 'client':
        return Api;
      case 'agent':
        return Bot;
      default:
        return User;
    }
  };

  const formatOperationState = (state: string) => {
    return state
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const headers = [
    {key: 'operationState', header: '', width: '48px'},
    {key: 'operationType', header: 'Operation'},
    {key: 'property', header: 'Property'},
    {key: 'user', header: 'Actor'},
    {key: 'startTimestamp', header: 'Date'},
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
      ).map((entry: MockAuditLogEntry) => {
        // Determine property display for variable operations
        let propertyDisplay: React.ReactNode = '-';
        if (
          entry.operationType === 'ADD_VARIABLE' ||
          entry.operationType === 'UPDATE_VARIABLE'
        ) {
          const variableName = entry.details?.variable?.name;
          if (variableName) {
            propertyDisplay = (
              <div>
                <div
                  style={{
                    fontSize: 'var(--cds-label-01-font-size)',
                    lineHeight: 'var(--cds-label-01-line-height)',
                    color: 'var(--cds-text-secondary)',
                  }}
                >
                  Variable name
                </div>
                {variableName}
              </div>
            );
          }
        }

        return {
          id: entry.id,
          operationType: formatOperationType(entry.operationType),
          operationState: entry.operationState,
          property: propertyDisplay,
          user: entry.user ? (() => {
            const actorType = getActorType(entry.user);
            const getActorTypeLabel = (type: 'user' | 'client' | 'agent') => {
              switch (type) {
                case 'client':
                  return 'API client';
                case 'agent':
                  return 'AI agent';
                default:
                  return 'User';
              }
            };
            const icon = actorType === 'user' ? <User size={16} /> : actorType === 'client' ? <Api size={16} /> : <Bot size={16} />;
            return (
              <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)'}}>
                <Tooltip align="top" description={getActorTypeLabel(actorType)}>
                  <div style={{color: 'var(--cds-icon-secondary)', flexShrink: 0, display: 'flex', alignItems: 'center', cursor: 'pointer'}}>
                    {icon}
                  </div>
                </Tooltip>
                <CodeSnippet
                  type="inline"
                  title="Click to copy"
                  aria-label="Click to copy"
                  feedback="Copied to clipboard"
                >
                  {formatUsername(entry.user)}
                </CodeSnippet>
              </div>
            );
          })() : '-',
          startTimestamp: formatDate(entry.startTimestamp),
          startTimestampRaw: entry.startTimestamp,
          entry: entry,
        };
      }) || [],
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
      <style>{`
        .operations-log-table .cds--popover[role='tooltip'] .cds--popover-content {
          padding-top: var(--cds-spacing-02);
          padding-bottom: var(--cds-spacing-02);
        }
      `}</style>
      <div
        className="operations-log-table"
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
                      isSortable: header.key !== 'actions' && header.key !== 'operationState' && header.key !== 'property',
                    });
                    const headerStyle = 
                      header.key === 'actions' ? {width: '72px'} :
                      header.key === 'operationState' ? {width: '40px', minWidth: '40px', maxWidth: '40px', padding: '0 var(--cds-spacing-03)'} : {};
                    return (
                      <TableHeader
                        {...headerProps}
                        key={key}
                        style={headerStyle}
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
                        if (cell.info.header === 'operationState') {
                          if (!rowData) {
                            return <TableCell key={cell.id} style={{width: '32px', minWidth: '32px', maxWidth: '32px', padding: '0 var(--cds-spacing-03)'}}>-</TableCell>;
                          }
                          const isSuccess = rowData.entry.operationState === 'success';
                          const Icon = isSuccess ? CheckmarkOutline : Error;
                          const color = isSuccess
                            ? 'var(--cds-support-success)'
                            : 'var(--cds-support-error)';
                          return (
                            <TableCell key={cell.id} style={{width: '40px', minWidth: '40px', maxWidth: '40px', padding: '0 var(--cds-spacing-04)'}}>
                              <Icon size={16} style={{color}} />
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
