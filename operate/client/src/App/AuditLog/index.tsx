/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
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
import {Information} from '@carbon/react/icons';
import {Stack} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';

const AuditLog: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

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
      processInstanceState:
        (params.get('processInstanceState') as
          | AuditLogSearchFilters['processInstanceState']
          | null) || undefined,
      operationType:
        (params.get('operationType') as
          | AuditLogSearchFilters['operationType']
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
      tenantId: params.get('tenantId') || undefined,
      searchQuery: params.get('searchQuery') || undefined,
    };
  }, [location.search]);

  const isTenancyEnabled = true; // Assumed enabled based on requirements

  // Helper function to update URL with new filters
  const updateFilters = (newFilters: AuditLogSearchFilters) => {
    const newParams = new URLSearchParams(location.search);

    // Define all possible filter keys
    const filterKeys = [
      'processDefinitionName',
      'processDefinitionVersion',
      'processInstanceKey',
      'processInstanceState',
      'operationType',
      'operationState',
      'startDateFrom',
      'startDateTo',
      'endDateFrom',
      'endDateTo',
      'user',
      'note',
      'tenantId',
      'searchQuery',
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
        from: 0,
        limit: 50,
      },
    }),
    [filtersFromUrl, sortBy, sortOrder],
  );

  const {data, isLoading, error} = useAuditLog(request);

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
    {key: 'processDefinitionName', header: 'Process definition'},
    {key: 'operationType', header: 'Operation'},
    {key: 'operationState', header: 'Status'},
    {key: 'startTimestamp', header: 'Start timestamp'},
    {key: 'user', header: 'User'},
    {key: 'note', header: 'Note'},
  ];

  const rows = useMemo(
    () =>
      data?.items.map((entry: AuditLogEntry) => ({
        id: entry.id,
        processDefinitionName: entry.processDefinitionName,
        operationType: formatOperationType(entry.operationType),
        operationState: formatOperationState(entry.operationState),
        startTimestamp: formatDate(entry.startTimestamp),
        user: entry.user,
        note: entry.comment ? (
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
      })) || [],
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
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: '1rem',
          paddingTop: '0.5rem',
          flex: 1,
          overflow: 'auto',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Stack gap={5}>
          <Stack orientation="vertical" gap={1}>
            <div style={{padding: '1rem', paddingBottom: 0}}>
              <h4 style={{marginBottom: '1rem'}}>Filter</h4>
            </div>
            <AuditLogFilters
              filters={filtersFromUrl}
              onFiltersChange={(newFilters) => {
                updateFilters(newFilters);
              }}
              onSearchChange={(query) => {
                updateFilters({...filtersFromUrl, searchQuery: query});
              }}
              isTenancyEnabled={isTenancyEnabled}
            />
          </Stack>

          <Stack orientation="vertical" gap={1}>
            <div style={{padding: '1rem', paddingBottom: 0}}>
              <h4 style={{marginBottom: '1rem'}}>Operations</h4>
            </div>
            <SortableTable
              state={isLoading ? 'skeleton' : error ? 'error' : 'content'}
              headerColumns={headers}
              rows={sortedRows}
              onSort={(clickedSortKey) => {
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
          </Stack>
        </Stack>
      </div>
    </div>
  );
};

export {AuditLog};
