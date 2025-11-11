/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useCallback, useEffect, useMemo} from 'react';
import {
  Stack,
  Search,
  Dropdown,
  TextInput,
  FilterableMultiSelect,
  Layer,
  DatePicker,
  DatePickerInput,
} from '@carbon/react';
import type {
  OperationType,
  OperationState,
  ProcessInstanceState,
  AuditLogSearchFilters,
} from 'modules/api/v2/auditLog/searchAuditLog';
import {observer} from 'mobx-react';

const OPERATION_TYPES: {id: OperationType; label: string}[] = [
  {id: 'RESOLVE_INCIDENT', label: 'Resolve incidents'},
  {id: 'CANCEL_PROCESS_INSTANCE', label: 'Cancel process instance'},
  {id: 'MIGRATE_PROCESS_INSTANCE', label: 'Migrate process instance'},
  {id: 'MODIFY_PROCESS_INSTANCE', label: 'Modify process instance'},
  {id: 'DELETE_PROCESS_INSTANCE', label: 'Delete process instance'},
  {id: 'DELETE_PROCESS_DEFINITION', label: 'Delete process definition'},
  {id: 'ADD_VARIABLE', label: 'Add variable'},
  {id: 'UPDATE_VARIABLE', label: 'Update variable'},
  {id: 'DELETE_DECISION_DEFINITION', label: 'Delete decision definition'},
];

const OPERATION_STATES: {id: OperationState; label: string}[] = [
  {id: 'CREATED', label: 'Created'},
  {id: 'ACTIVE', label: 'Active'},
  {id: 'SUSPENDED', label: 'Suspended'},
  {id: 'COMPLETED', label: 'Completed'},
  {id: 'PARTIALLY_COMPLETED', label: 'Partially completed'},
  {id: 'CANCELLED', label: 'Cancelled'},
];

const PROCESS_INSTANCE_STATES: {id: ProcessInstanceState; label: string}[] = [
  {id: 'ACTIVE', label: 'Active'},
  {id: 'COMPLETED', label: 'Completed'},
  {id: 'CANCELED', label: 'Canceled'},
  {id: 'INCIDENT', label: 'Incident'},
  {id: 'TERMINATED', label: 'Terminated'},
];

type AuditLogFiltersProps = {
  filters: AuditLogSearchFilters;
  onFiltersChange: (filters: AuditLogSearchFilters) => void;
  onSearchChange: (query: string) => void;
  isTenancyEnabled: boolean;
};

const AuditLogFilters: React.FC<AuditLogFiltersProps> = observer(
  ({filters, onFiltersChange, onSearchChange, isTenancyEnabled}) => {
    const [localSearchQuery, setLocalSearchQuery] = useState(
      filters.searchQuery || '',
    );

    // Debounce search
    useEffect(() => {
      const timeoutId = setTimeout(() => {
        onSearchChange(localSearchQuery);
      }, 300);

      return () => clearTimeout(timeoutId);
    }, [localSearchQuery, onSearchChange]);

    const handleFilterChange = useCallback(
      (
        key: keyof AuditLogSearchFilters,
        value: string | number | undefined,
      ) => {
        onFiltersChange({...filters, [key]: value});
      },
      [filters, onFiltersChange],
    );

    // Memoize selected items to prevent infinite re-renders
    const selectedOperationTypes = useMemo(
      () =>
        filters.operationType
          ? OPERATION_TYPES.filter((t) => t.id === filters.operationType)
          : [],
      [filters.operationType],
    );

    const selectedOperationStates = useMemo(
      () =>
        filters.operationState
          ? OPERATION_STATES.filter((s) => s.id === filters.operationState)
          : [],
      [filters.operationState],
    );

    return (
      <Layer>
        <Stack gap={5}>
          {/* Filter row 1 - Main filters */}
          <Stack orientation="horizontal" gap={5}>
            <TextInput
              id="process-definition-name"
              labelText="Process definition name"
              placeholder="All processes"
              value={filters.processDefinitionName || ''}
              onChange={(e) =>
                handleFilterChange('processDefinitionName', e.target.value)
              }
              size="sm"
            />
            <TextInput
              id="process-definition-version"
              labelText="Version"
              type="number"
              placeholder="All versions"
              value={filters.processDefinitionVersion || ''}
              onChange={(e) =>
                handleFilterChange(
                  'processDefinitionVersion',
                  e.target.value ? Number(e.target.value) : undefined,
                )
              }
              size="sm"
              style={{maxWidth: '150px'}}
            />
            <TextInput
              id="process-instance-key"
              labelText="Process instance key"
              placeholder="Filter by instance key"
              value={filters.processInstanceKey || ''}
              onChange={(e) =>
                handleFilterChange('processInstanceKey', e.target.value)
              }
              size="sm"
            />
            <Dropdown
              id="process-instance-state"
              titleText="Process instance state"
              label="Choose option(s)"
              items={PROCESS_INSTANCE_STATES}
              itemToString={(item) => item?.label ?? ''}
              selectedItem={
                filters.processInstanceState
                  ? PROCESS_INSTANCE_STATES.find(
                      (s) => s.id === filters.processInstanceState,
                    )
                  : null
              }
              onChange={({selectedItem}) =>
                handleFilterChange(
                  'processInstanceState',
                  selectedItem?.id ?? undefined,
                )
              }
              size="sm"
            />
          </Stack>

          {/* Filter row 2 - Operation, dates, user */}
          <Stack orientation="horizontal" gap={5}>
            <FilterableMultiSelect
              id="operation-type"
              titleText="Operation type"
              placeholder="Choose option(s)"
              items={OPERATION_TYPES}
              itemToString={(item) => item?.label ?? ''}
              selectedItems={selectedOperationTypes}
              onChange={({selectedItems}) =>
                handleFilterChange(
                  'operationType',
                  selectedItems[0]?.id ?? undefined,
                )
              }
              size="sm"
            />
            <FilterableMultiSelect
              id="operation-state"
              titleText="Operation status"
              placeholder="Choose option(s)"
              items={OPERATION_STATES}
              itemToString={(item) => item?.label ?? ''}
              selectedItems={selectedOperationStates}
              onChange={({selectedItems}) =>
                handleFilterChange(
                  'operationState',
                  selectedItems[0]?.id ?? undefined,
                )
              }
              size="sm"
            />
            <DatePicker
              datePickerType="range"
              value={[filters.startDateFrom || '', filters.startDateTo || '']}
              onChange={(dates) => {
                const [startDate, endDate] = dates;
                handleFilterChange('startDateFrom', startDate?.toISOString() || '');
                handleFilterChange('startDateTo', endDate?.toISOString() || '');
              }}
            >
              <DatePickerInput
                id="start-date"
                labelText="From"
                placeholder="mm/dd/yyyy"
                size="sm"
              />
              <DatePickerInput
                id="end-date"
                labelText="To"
                placeholder="mm/dd/yyyy"
                size="sm"
              />
            </DatePicker>
            <TextInput
              id="user"
              labelText="User"
              placeholder="Filter by user"
              value={filters.user || ''}
              onChange={(e) => handleFilterChange('user', e.target.value)}
              size="sm"
            />
            {isTenancyEnabled && (
              <TextInput
                id="tenant-id"
                labelText="Tenant id"
                placeholder="Filter by tenant"
                value={filters.tenantId || ''}
                onChange={(e) => handleFilterChange('tenantId', e.target.value)}
                size="sm"
              />
            )}
          </Stack>

          {/* Search bar - below filters as per design */}
          <Search
            labelText="Search"
            placeholder="Search user or comment"
            value={localSearchQuery}
            onChange={(e) => setLocalSearchQuery(e.target.value)}
            onClear={() => setLocalSearchQuery('')}
            size="sm"
          />
        </Stack>
      </Layer>
    );
  },
);

export {AuditLogFilters};
