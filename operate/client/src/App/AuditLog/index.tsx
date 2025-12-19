/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo, useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {Link, Pagination, Button} from '@carbon/react';
import {AuditLogFilters} from './Filters';
import {
  type AuditLogSearchFilters,
  type AuditLogEntry,
  type SortField,
  type SortOrder,
  type AuditLogSearchRequest,
} from 'modules/api/v2/auditLog/searchAuditLog';
import {useAuditLog} from 'modules/queries/auditLog/useAuditLog';
import {SortableTable} from 'modules/components/SortableTable';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {PAGE_TITLE} from 'modules/constants';
import {Information} from '@carbon/react/icons';
import {DetailsModal} from './DetailsModal';
import {StatusIndicator} from './StatusIndicator';
import type {MockAuditLogEntry} from 'modules/mocks/auditLog';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {FiltersPanel} from 'modules/components/FiltersPanel';

type DetailsModalState = {
  open: boolean;
  entry: MockAuditLogEntry | null;
};

const AuditLog: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [detailsModal, setDetailsModal] = useState<DetailsModalState>({
    open: false,
    entry: null,
  });

  useEffect(() => {
    document.title = PAGE_TITLE.OPERATIONS_LOG;
  }, []);

  // Get sort from URL or use defaults
  const sortParams = getSortParams(location.search);
  const sortBy = (sortParams?.sortBy as SortField) || 'startTimestamp';
  const sortOrder =
    (sortParams?.sortOrder?.toUpperCase() as SortOrder) || 'DESC';

  // Get filters from URL and memoize them to avoid re-renders
  const filtersFromUrl: AuditLogSearchFilters = useMemo(() => {
    const params = new URLSearchParams(location.search);
    return {
      processDefinitionName: params.get('processDefinitionName') || undefined,
      processDefinitionVersion: params.get('processDefinitionVersion')
        ? Number(params.get('processDefinitionVersion'))
        : undefined,
      processInstanceKey: params.get('processInstanceKey') || undefined,
      operationType:
        (params.get('operationType') as
          | AuditLogSearchFilters['operationType']
          | null) || undefined,
      operationEntity:
        (params.get('operationEntity') as
          | AuditLogSearchFilters['operationEntity']
          | null) || undefined,
      operationState:
        (params.get('operationState') as
          | AuditLogSearchFilters['operationState']
          | null) || undefined,
      startDateFrom: params.get('startDateFrom') || undefined,
      startDateTo: params.get('startDateTo') || undefined,
      endDateFrom: params.get('endDateFrom') || undefined,
      endDateTo: params.get('endDateTo') || undefined,
      user: params.get('user') || undefined,
      note: params.get('note') || undefined,
    };
  }, [location.search]);

  // Helper function to update URL with new filters
  const updateFilters = (newFilters: AuditLogSearchFilters) => {
    const newParams = new URLSearchParams(location.search);

    // Define all possible filter keys
    const filterKeys = [
      'processDefinitionName',
      'processDefinitionVersion',
      'processInstanceKey',
      'operationType',
      'operationEntity',
      'operationState',
      'startDateFrom',
      'startDateTo',
      'endDateFrom',
      'endDateTo',
      'user',
      'note',
    ];

    // Remove all filter params first
    filterKeys.forEach((key) => {
      newParams.delete(key);
    });

    // Add new filter params
    Object.entries(newFilters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        newParams.set(key, String(value));
      }
    });

    navigate({search: newParams.toString()}, {replace: true});
  };

  // Build request body from filters and pagination
  const request: AuditLogSearchRequest = useMemo(
    () => ({
      sort: [
        {
          field: sortBy,
          order: sortOrder,
        },
      ],
      filter: filtersFromUrl,
      page: {
        from: (currentPage - 1) * pageSize,
        limit: pageSize,
      },
    }),
    [filtersFromUrl, sortBy, sortOrder, currentPage, pageSize],
  );

  const {data, isLoading, error} = useAuditLog(request);

  // Reset to first page when filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [filtersFromUrl]);

  const openDetailsModal = (entry: MockAuditLogEntry) => {
    setDetailsModal({open: true, entry});
  };

  const closeDetailsModal = () => {
    setDetailsModal({open: false, entry: null});
  };

  const operationTypeDisplayMap: Record<string, string> = {
    CREATE_PROCESS_INSTANCE: 'Create',
    CANCEL_PROCESS_INSTANCE: 'Cancel',
    MODIFY_PROCESS_INSTANCE: 'Modify',
    MIGRATE_PROCESS_INSTANCE: 'Migrate',
    DEPLOY_RESOURCE: 'Delete',
    DELETE_RESOURCE: 'Delete',
  };

  const formatOperationType = (type: string) => {
    if (operationTypeDisplayMap[type]) {
      return operationTypeDisplayMap[type];
    }

    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const formatOperationEntity = (
    entity: MockAuditLogEntry['operationEntity'],
  ) => {
    if (!entity) {
      return '-';
    }

    return entity
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const resolveOperationEntity = (entry: MockAuditLogEntry) => {
    if (entry.operationEntity) {
      return entry.operationEntity;
    }

    if (entry.isMultiInstanceOperation) {
      return 'BATCH';
    }

    if (entry.operationType === 'EVALUATE_DECISION') {
      return 'DECISION_INSTANCE';
    }

    if (entry.operationType.includes('RESOURCE')) {
      return 'RESOURCE';
    }

    return 'PROCESS_INSTANCE';
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


  const headers = [
    {key: 'operationType', header: 'Operation type'},
    {key: 'operationEntity', header: 'Entity'},
    {key: 'processes', header: 'Reference'},
    {key: 'operationState', header: 'Status'},
    {key: 'user', header: 'Actor'},
    {key: 'startTimestamp', header: 'Time'},
    {key: 'actions', header: ' '},
  ];

  const rows = useMemo(
    () =>
      data?.items.map((entry: AuditLogEntry) => {
        const mockEntry = entry as MockAuditLogEntry;
        
        // Determine what to show in the "Reference" column
        let processesDisplay: React.ReactNode;
        
        if (mockEntry.isMultiInstanceOperation) {
          // For batch operations, show "Batch operation" text and batch key as link
          processesDisplay = (
            <div>
              <div>Multiple process instances</div>
              {mockEntry.batchOperationId && (
                <Link
                  href="#"
                  onClick={(e: React.MouseEvent) => e.preventDefault()}
                  style={{
                    fontSize: '0.75rem',
                  }}
                >
                  {mockEntry.batchOperationId}
                </Link>
              )}
            </div>
          );
        } else if (
          entry.operationType === 'DEPLOY_RESOURCE' ||
          entry.operationType === 'DELETE_RESOURCE'
        ) {
          // For resource operations, show resource name and key
          const resourceKey = mockEntry.details?.resourceKey;
          const resourceType = mockEntry.details?.resourceType;
          const isForm = resourceType === 'form';
          const isDelete = entry.operationType === 'DELETE_RESOURCE';
          
          processesDisplay = (
            <div>
              <div>{entry.processDefinitionName}</div>
              {resourceKey && (
                isForm || isDelete ? (
                  <div
                    style={{
                      fontSize: '0.75rem',
                      color: 'var(--cds-text-secondary)',
                    }}
                  >
                    {resourceKey}
                  </div>
                ) : (
                  <Link
                    href="#"
                    onClick={(e: React.MouseEvent) => e.preventDefault()}
                    style={{
                      fontSize: '0.75rem',
                    }}
                  >
                    {resourceKey}
                  </Link>
                )
              )}
            </div>
          );
        } else if (entry.processDefinitionName) {
          // For single operations, show process name with instance key as link
          processesDisplay = (
            <div>
              <div>{entry.processDefinitionName}</div>
              {entry.processInstanceKey && (
                <Link
                  href="#"
                  onClick={(e: React.MouseEvent) => e.preventDefault()}
                  style={{
                    fontSize: '0.75rem',
                  }}
                >
                  {entry.processInstanceKey}
                </Link>
              )}
            </div>
          );
        } else {
          processesDisplay = '-';
        }

        return {
          id: entry.id,
          operationType: (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <span>{formatOperationType(entry.operationType)}</span>
            </div>
          ),
          operationEntity: formatOperationEntity(resolveOperationEntity(mockEntry)),
          operationState: <StatusIndicator status={entry.operationState} />,
          processes: processesDisplay,
          user: formatUsername(entry.user),
          startTimestamp: formatDate(entry.startTimestamp),
          actions:
            entry.operationState === 'fail' ||
            entry.operationType === 'ADD_VARIABLE' ||
            entry.operationType === 'UPDATE_VARIABLE' ? (
              <Button
                kind="ghost"
                size="sm"
                hasIconOnly
                renderIcon={Information}
                onClick={() => openDetailsModal(mockEntry)}
                iconDescription="View details"
              />
            ) : null,
          entry: mockEntry,
        };
      }) || [],
    [data],
  );

  // TODO: REMOVE THIS - Frontend sorting simulation for demo purposes only
  // Backend should handle sorting and return sorted data
  const sortedRows = useMemo(() => {
    if (!rows.length) {
      return rows;
    }

    const rowsCopy = [...rows];
    const rawData = data?.items || [];

    return rowsCopy.sort((a, b) => {
      let aValue: string | number = '';
      let bValue: string | number = '';

      // Get raw values for comparison
      const aIndex = rowsCopy.indexOf(a);
      const bIndex = rowsCopy.indexOf(b);
      const aEntry = rawData[aIndex];
      const bEntry = rawData[bIndex];

      switch (sortBy) {
        case 'processDefinitionName':
          aValue = aEntry?.processDefinitionName || '';
          bValue = bEntry?.processDefinitionName || '';
          break;
        case 'operationType':
          aValue = aEntry?.operationType || '';
          bValue = bEntry?.operationType || '';
          break;
        case 'operationEntity':
          aValue = resolveOperationEntity(aEntry as MockAuditLogEntry);
          bValue = resolveOperationEntity(bEntry as MockAuditLogEntry);
          break;
        case 'operationState':
          aValue = aEntry?.operationState || '';
          bValue = bEntry?.operationState || '';
          break;
        case 'startTimestamp':
          aValue = new Date(aEntry?.startTimestamp || 0).getTime();
          bValue = new Date(bEntry?.startTimestamp || 0).getTime();
          break;
        case 'user':
          aValue = aEntry?.user || '';
          bValue = bEntry?.user || '';
          break;
        default:
          return 0;
      }

      if (typeof aValue === 'string' && typeof bValue === 'string') {
        const comparison = aValue.localeCompare(bValue);
        return sortOrder === 'ASC' ? comparison : -comparison;
      }

      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return sortOrder === 'ASC' ? aValue - bValue : bValue - aValue;
      }

      return 0;
    });
  }, [rows, sortBy, sortOrder, data]);

  return (
    <>
      <VisuallyHiddenH1>Audit Log</VisuallyHiddenH1>
      <div
        style={{
          display: 'flex',
          height: '100%',
          overflow: 'hidden',
        }}
      >
        {/* Left Panel - Filters */}
        <FiltersPanel
          localStorageKey="isFiltersCollapsed"
          isResetButtonDisabled={Object.values(filtersFromUrl).every(
            (value) => value === undefined,
          )}
          onResetClick={() => {
            updateFilters({});
          }}
        >
          <AuditLogFilters
            filters={filtersFromUrl}
            onFiltersChange={(newFilters) => {
              updateFilters(newFilters);
            }}
          />
        </FiltersPanel>

        {/* Right Panel - Table */}
        <div
          style={{
            flex: 1,
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            backgroundColor: 'var(--cds-layer)',
            overflow: 'hidden',
          }}
        >
          <div style={{padding: 'var(--cds-spacing-05)', paddingBottom: 0}}>
            <h3 style={{marginBottom: 'var(--cds-spacing-05)'}}>Operations Log</h3>
          </div>
          <div 
            style={{
              flex: 1, 
              overflow: 'auto', 
              padding: '0 var(--cds-spacing-05)'
            }}
            className="audit-log-table-container"
          >
            <style>{`
              .audit-log-table-container thead {
                position: sticky;
                top: 0;
                z-index: 1;
                background-color: var(--cds-layer);
              }
            `}</style>
            <SortableTable
              state={isLoading ? 'skeleton' : error ? 'error' : 'content'}
              headerColumns={headers}
              rows={sortedRows}
              onSort={(clickedSortKey) => {
                // Don't allow sorting on actions column
                if (clickedSortKey === 'actions') {
                  return;
                }

                const newParams = new URLSearchParams(location.search);
                const currentSort = getSortParams(location.search);

                if (clickedSortKey === currentSort?.sortBy) {
                  // Toggle sort order if same column
                  const newOrder =
                    currentSort.sortOrder === 'asc' ? 'desc' : 'asc';
                  newParams.set('sort', `${clickedSortKey}+${newOrder}`);
                } else {
                  // New column, default to DESC
                  newParams.set('sort', `${clickedSortKey}+desc`);
                }

                navigate({search: newParams.toString()}, {replace: true});
              }}
            />
          </div>
          <Pagination
            page={currentPage}
            pageSize={pageSize}
            pageSizes={[50, 100, 200]}
            totalItems={data?.totalCount || 0}
            onChange={({page, pageSize: newPageSize}) => {
              setCurrentPage(page);
              setPageSize(newPageSize);
            }}
          />
        </div>
      </div>

      <DetailsModal
        open={detailsModal.open}
        onClose={closeDetailsModal}
        entry={detailsModal.entry}
      />
    </>
  );
};

export {AuditLog};
