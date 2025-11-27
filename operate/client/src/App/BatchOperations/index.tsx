/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {Link} from '@carbon/react';
import {ArrowLeft, Checkmark, Error} from '@carbon/icons-react';
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
    return {
      processDefinitionName: params.get('processDefinitionName') || undefined,
      processDefinitionVersion: params.get('processDefinitionVersion')
        ? Number(params.get('processDefinitionVersion'))
        : undefined,
      processInstanceState: params.get('processInstanceState') || undefined,
      processInstanceKey: params.get('processInstanceKey') || undefined,
      operationType:
        (params.get('operationType') as
          | BatchOperationsFiltersType['operationType']
          | null) || undefined,
      operationState:
        (params.get('operationState') as
          | BatchOperationsFiltersType['operationState']
          | null) || undefined,
      startDateFrom: params.get('startDateFrom') || undefined,
      startDateTo: params.get('startDateTo') || undefined,
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
      'operationType',
      'operationState',
      'startDateFrom',
      'startDateTo',
      'user',
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
    if (filtersFromUrl.operationType) {
      result = result.filter(
        (op) => op.operationType === filtersFromUrl.operationType,
      );
    }
    if (filtersFromUrl.operationState) {
      result = result.filter(
        (op) => op.state === filtersFromUrl.operationState,
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
        // Build items display with success/failed counts
        let itemsDisplay: React.ReactNode;
        const successCount = operation.completedItems;
        const failedCount = operation.failedItems;
        const hasStatus = successCount > 0 || failedCount > 0;

        if (operation.state === 'ACTIVE' || operation.state === 'CREATED' || !hasStatus) {
          // Show total count greyed out when no status available yet
          itemsDisplay = (
            <span style={{color: 'var(--cds-text-secondary)'}}>
              {operation.totalItems}
            </span>
          );
        } else {
          itemsDisplay = (
            <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)'}}>
              {successCount > 0 && (
                <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)'}}>
                  <Checkmark size={16} style={{color: 'var(--cds-support-success)'}} />
                  {successCount}
                </span>
              )}
              {failedCount > 0 && (
                <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)'}}>
                  <Error size={16} style={{color: 'var(--cds-support-error)'}}/>
                  {failedCount}
                </span>
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
      <div
        style={{
          display: 'flex',
          height: '100%',
          overflow: 'hidden',
        }}
      >
        {/* Left Panel - Filters */}
        <FiltersPanel
          localStorageKey="isBatchOperationsFiltersCollapsed"
          isResetButtonDisabled={Object.values(filtersFromUrl).every(
            (value) => value === undefined,
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
          <div
            style={{
              padding: 'var(--cds-spacing-05)',
              paddingBottom: 0,
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--cds-spacing-05)',
            }}
          >
            <Link
              href="#"
              onClick={(e: React.MouseEvent) => {
                e.preventDefault();
                navigate(Paths.processes());
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
          </div>
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

