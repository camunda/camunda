/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {Link, Breadcrumb, BreadcrumbItem, Tooltip} from '@carbon/react';
import {Checkmark, Error, CircleDash} from '@carbon/icons-react';
import {
  BatchOperationsFilters,
  type BatchOperationsFilters as BatchOperationsFiltersType,
} from './Filters';
import {SortableTable} from 'modules/components/SortableTable';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {PAGE_TITLE} from 'modules/constants';
import {BatchOperationStatusTag} from './BatchOperationStatusTag';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {FiltersPanel} from 'modules/components/FiltersPanel';
import {mockBatchOperations} from 'modules/mocks/batchOperations';
import {Paths} from 'modules/Routes';

const formatOperationType = (type: string) => {
  return type
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
};

const BatchOperations: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    document.title = PAGE_TITLE.BATCH_OPERATIONS;
  }, []);

  // Get sort from URL or use defaults
  const sortParams = getSortParams(location.search);
  const sortBy = sortParams?.sortBy || 'startTime';
  const sortOrder = sortParams?.sortOrder?.toUpperCase() || 'DESC';

  // Get filters from URL and memoize them to avoid re-renders
  const filtersFromUrl: BatchOperationsFiltersType = useMemo(() => {
    const params = new URLSearchParams(location.search);
    const operationTypesParam = params.get('operationTypes');
    const operationStatesParam = params.get('operationStates');
    return {
      processDefinitionName: params.get('processDefinitionName') || undefined,
      processDefinitionVersion: params.get('processDefinitionVersion')
        ? Number(params.get('processDefinitionVersion'))
        : undefined,
      processInstanceState: params.get('processInstanceState') || undefined,
      processInstanceKey: params.get('processInstanceKey') || undefined,
      operationTypes: operationTypesParam
        ? (operationTypesParam.split(',') as BatchOperationsFiltersType['operationTypes'])
        : undefined,
      operationStates: operationStatesParam
        ? (operationStatesParam.split(',') as BatchOperationsFiltersType['operationStates'])
        : undefined,
      startDateFrom: params.get('startDateFrom') || undefined,
      startDateTo: params.get('startDateTo') || undefined,
      endDateFrom: params.get('endDateFrom') || undefined,
      endDateTo: params.get('endDateTo') || undefined,
      user: params.get('user') || undefined,
    };
  }, [location.search]);

  // Helper function to update URL with new filters
  const updateFilters = (newFilters: BatchOperationsFiltersType) => {
    const newParams = new URLSearchParams(location.search);

    // Define all possible filter keys
    const filterKeys = [
      'processDefinitionName',
      'processDefinitionVersion',
      'processInstanceState',
      'processInstanceKey',
      'operationTypes',
      'operationStates',
      'startDateFrom',
      'startDateTo',
      'endDateFrom',
      'endDateTo',
      'user',
    ];

    // Remove all filter params first
    filterKeys.forEach((key) => {
      newParams.delete(key);
    });

    // Add new filter params
    Object.entries(newFilters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        // Handle array values (operationTypes, operationStates)
        if (Array.isArray(value) && value.length > 0) {
          newParams.set(key, value.join(','));
        } else if (!Array.isArray(value)) {
          newParams.set(key, String(value));
        }
      }
    });

    navigate({search: newParams.toString()}, {replace: true});
  };

  // Filter and sort data from mock
  const filteredData = useMemo(() => {
    let result = [...mockBatchOperations];

    // Apply filters
    if (filtersFromUrl.processDefinitionName) {
      result = result.filter(
        (op) =>
          op.processDefinitionName === filtersFromUrl.processDefinitionName,
      );
    }
    if (filtersFromUrl.processDefinitionVersion) {
      result = result.filter(
        (op) =>
          op.processDefinitionVersion ===
          filtersFromUrl.processDefinitionVersion,
      );
    }
    if (filtersFromUrl.operationTypes?.length) {
      result = result.filter((op) =>
        filtersFromUrl.operationTypes!.includes(op.operationType),
      );
    }
    if (filtersFromUrl.operationStates?.length) {
      result = result.filter((op) =>
        filtersFromUrl.operationStates!.includes(op.state),
      );
    }
    if (filtersFromUrl.user) {
      result = result.filter((op) =>
        op.appliedBy.toLowerCase().includes(filtersFromUrl.user!.toLowerCase()),
      );
    }

    // Sort - active operations first, then by start time
    result.sort((a, b) => {
      // Active operations always come first
      const aIsActive = a.state === 'ACTIVE' || a.state === 'CREATED';
      const bIsActive = b.state === 'ACTIVE' || b.state === 'CREATED';

      if (aIsActive && !bIsActive) return -1;
      if (!aIsActive && bIsActive) return 1;

      // Then sort by the selected field
      let comparison = 0;
      if (sortBy === 'startTime') {
        comparison =
          new Date(b.startTime).getTime() - new Date(a.startTime).getTime();
      } else if (sortBy === 'operationType') {
        comparison = a.operationType.localeCompare(b.operationType);
      } else if (sortBy === 'state') {
        comparison = a.state.localeCompare(b.state);
      } else if (sortBy === 'appliedBy') {
        comparison = a.appliedBy.localeCompare(b.appliedBy);
      }

      return sortOrder === 'DESC' ? comparison : -comparison;
    });

    return result;
  }, [filtersFromUrl, sortBy, sortOrder]);

  const headers = [
    {key: 'operationType', header: 'Operation', sortKey: 'operationType'},
    {key: 'state', header: 'Batch state', sortKey: 'state'},
    {key: 'items', header: 'Items'},
    {key: 'appliedBy', header: 'Applied by', sortKey: 'appliedBy'},
    {key: 'startTime', header: 'Start time', sortKey: 'startTime'},
  ];

  const rows = useMemo(
    () =>
      filteredData.map((operation) => {
        // Build items display with success/failed/pending counts
        const successCount = operation.completedItems;
        const failedCount = operation.failedItems;
        const pendingCount = operation.totalItems - successCount - failedCount;
        const hasAnyProgress = successCount > 0 || failedCount > 0;

        let itemsDisplay: React.ReactNode;

        if (!hasAnyProgress && pendingCount > 0) {
          // Show only pending count when nothing has started yet
          itemsDisplay = (
            <Tooltip description={`${pendingCount} not started`} align="bottom">
              <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)', color: 'var(--cds-text-secondary)', cursor: 'default'}}>
                <CircleDash size={16} />
                {pendingCount}
              </span>
            </Tooltip>
          );
        } else {
          // Show breakdown of success / failed / pending
          itemsDisplay = (
            <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)'}}>
              {successCount > 0 && (
                <Tooltip description={`${successCount} successful`} align="bottom">
                  <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)', cursor: 'default'}}>
                    <Checkmark size={16} style={{color: 'var(--cds-support-success)'}} />
                    {successCount}
                  </span>
                </Tooltip>
              )}
              {failedCount > 0 && (
                <Tooltip description={`${failedCount} failed`} align="bottom">
                  <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)', cursor: 'default'}}>
                    <Error size={16} style={{color: 'var(--cds-support-error)'}} />
                    {failedCount}
                  </span>
                </Tooltip>
              )}
              {pendingCount > 0 && (
                <Tooltip description={`${pendingCount} not started`} align="bottom">
                  <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)', color: 'var(--cds-text-secondary)', cursor: 'default'}}>
                    <CircleDash size={16} />
                    {pendingCount}
                  </span>
                </Tooltip>
              )}
            </div>
          );
        }

        return {
          id: operation.id,
          operationType: (
            <Link
              href="#"
              onClick={(e: React.MouseEvent) => {
                e.preventDefault();
                navigate(Paths.batchOperationDetails(operation.id));
              }}
            >
              {formatOperationType(operation.operationType)}
            </Link>
          ),
          state: <BatchOperationStatusTag status={operation.state} />,
          items: itemsDisplay,
          startTime: formatDate(operation.startTime),
          appliedBy: operation.appliedBy,
        };
      }),
    [filteredData, navigate],
  );

  return (
    <>
      <VisuallyHiddenH1>Batch Operations</VisuallyHiddenH1>
      {/* Breadcrumb - Full Width */}
      <div
        style={{
          width: '100%',
          backgroundColor: 'var(--cds-layer-01)',
          borderBottom: '1px solid var(--cds-border-subtle-01)',
          padding: 'var(--cds-spacing-04) var(--cds-spacing-05)',
        }}
      >
        <Breadcrumb noTrailingSlash>
          <BreadcrumbItem>
            <Link
              href="#"
              onClick={(e: React.MouseEvent) => {
                e.preventDefault();
                navigate(Paths.processes());
              }}
            >
              Processes
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isCurrentPage>Operations</BreadcrumbItem>
        </Breadcrumb>
      </div>
      <div
        style={{
          display: 'flex',
          height: 'calc(100% - 40px)',
          overflow: 'hidden',
        }}
      >
        {/* Left Panel - Filters */}
        <FiltersPanel
          localStorageKey="isBatchOperationsFiltersCollapsed"
          isResetButtonDisabled={Object.values(filtersFromUrl).every(
            (value) => value === undefined || (Array.isArray(value) && value.length === 0),
          )}
          onResetClick={() => {
            updateFilters({});
          }}
        >
          <BatchOperationsFilters
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
            <h3 style={{marginBottom: 'var(--cds-spacing-06)'}}>
              Process Operations
            </h3>
          </div>
          <div
            style={{
              flex: 1,
              overflow: 'auto',
              padding: '0 var(--cds-spacing-05) var(--cds-spacing-05)',
            }}
            className="batch-operations-table-container"
          >
            <style>{`
              .batch-operations-table-container thead {
                position: sticky;
                top: 0;
                z-index: 1;
                background-color: var(--cds-layer);
              }
              .batch-operations-table-container .cds--popover-content {
                padding: var(--cds-spacing-02) var(--cds-spacing-03);
                font-size: var(--cds-body-compact-01-font-size);
                max-width: 150px;
              }
            `}</style>
            <SortableTable
              state={rows.length === 0 ? 'empty' : 'content'}
              headerColumns={headers}
              rows={rows}
              emptyMessage={{
                message: 'No batch operations found',
                additionalInfo:
                  'Try adjusting your filters or check back later.',
              }}
              onSort={(clickedSortKey) => {
                if (clickedSortKey === 'items') {
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
        </div>
      </div>
    </>
  );
};

export {BatchOperations};

