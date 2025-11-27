/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {
  Stack,
  TextInput,
  FilterableMultiSelect,
  ComboBox,
  Layer,
  DatePicker,
  DatePickerInput,
} from '@carbon/react';
import {observer} from 'mobx-react';
import {Title} from 'modules/components/FiltersPanel/styled';
import type {
  BatchOperationType,
  BatchOperationState,
} from 'modules/mocks/batchOperations';

const OPERATION_TYPES: {id: BatchOperationType; label: string}[] = [
  {id: 'RESOLVE_INCIDENT', label: 'Resolve incidents'},
  {id: 'MODIFY_PROCESS_INSTANCE', label: 'Modify process instance'},
  {id: 'MIGRATE_PROCESS_INSTANCE', label: 'Migrate process instance'},
  {id: 'CANCEL_PROCESS_INSTANCE', label: 'Cancel process instance'},
];

const OPERATION_STATES: {id: BatchOperationState; label: string}[] = [
  {id: 'CREATED', label: 'Created'},
  {id: 'ACTIVE', label: 'Active'},
  {id: 'COMPLETED', label: 'Completed'},
  {id: 'PARTIALLY_COMPLETED', label: 'Partially completed'},
  {id: 'SUSPENDED', label: 'Suspended'},
  {id: 'CANCELLED', label: 'Cancelled'},
  {id: 'FAILED', label: 'Failed'},
];

const PROCESS_INSTANCE_STATES: {id: string; label: string}[] = [
  {id: 'ACTIVE', label: 'Active'},
  {id: 'INCIDENT', label: 'Incident'},
  {id: 'COMPLETED', label: 'Completed'},
  {id: 'CANCELED', label: 'Canceled'},
];

// Process definitions with versions - in production, this would come from an API
const PROCESS_DEFINITIONS_WITH_VERSIONS: Record<string, number[]> = {
  'Order Process': [3, 2, 1],
  'Payment Process': [2, 1],
  'Claims Process': [2, 1],
  'Fulfillment Process': [1],
  'Check in process': [2, 1],
  'Legacy Process': [1],
  'Testing Process': [1],
  'Pricing Process': [1],
  'Decision Process': [1],
};

const PROCESS_DEFINITIONS = Object.keys(PROCESS_DEFINITIONS_WITH_VERSIONS);

export type BatchOperationsFilters = {
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  processInstanceState?: string;
  processInstanceKey?: string;
  operationType?: BatchOperationType;
  operationState?: BatchOperationState;
  startDateFrom?: string;
  startDateTo?: string;
  user?: string;
};

type BatchOperationsFiltersProps = {
  filters: BatchOperationsFilters;
  onFiltersChange: (filters: BatchOperationsFilters) => void;
};

const BatchOperationsFilters: React.FC<BatchOperationsFiltersProps> = observer(
  ({filters, onFiltersChange}) => {
    const handleFilterChange = useCallback(
      (
        key: keyof BatchOperationsFilters,
        value: string | number | undefined,
      ) => {
        onFiltersChange({...filters, [key]: value});
      },
      [filters, onFiltersChange],
    );

    // Get available versions for selected process
    const availableVersions = useMemo(() => {
      if (!filters.processDefinitionName) {
        return [];
      }
      return (
        PROCESS_DEFINITIONS_WITH_VERSIONS[filters.processDefinitionName] || []
      );
    }, [filters.processDefinitionName]);

    // Auto-select latest version when process definition changes
    const handleProcessDefinitionChange = useCallback(
      (selectedProcess: string | null) => {
        if (selectedProcess) {
          const versions =
            PROCESS_DEFINITIONS_WITH_VERSIONS[selectedProcess];
          const latestVersion = versions?.[0]; // First item is the latest
          onFiltersChange({
            ...filters,
            processDefinitionName: selectedProcess,
            processDefinitionVersion: latestVersion,
          });
        } else {
          onFiltersChange({
            ...filters,
            processDefinitionName: undefined,
            processDefinitionVersion: undefined,
          });
        }
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

    const selectedProcessInstanceStates = useMemo(
      () =>
        filters.processInstanceState
          ? PROCESS_INSTANCE_STATES.filter(
              (s) => s.id === filters.processInstanceState,
            )
          : [],
      [filters.processInstanceState],
    );

    return (
      <Layer>
        <div style={{padding: 'var(--cds-spacing-05)'}}>
          <Stack gap={8} orientation="vertical" style={{width: '100%'}}>
            <Stack gap={5} orientation="vertical" style={{width: '100%'}}>
              <Title style={{paddingBottom: '0'}}>Process</Title>
              <ComboBox
                id="process-definition-name"
                titleText="Name"
                placeholder="Search by Process Name"
                items={PROCESS_DEFINITIONS}
                selectedItem={filters.processDefinitionName || null}
                onChange={({selectedItem}) =>
                  handleProcessDefinitionChange(selectedItem ?? null)
                }
                size="sm"
                style={{width: '100%'}}
                light={true}
              />
              <ComboBox
                id="process-definition-version"
                titleText="Version"
                placeholder={
                  filters.processDefinitionName
                    ? 'Select Version'
                    : 'Select process first'
                }
                items={availableVersions}
                selectedItem={
                  filters.processDefinitionVersion !== undefined
                    ? filters.processDefinitionVersion
                    : null
                }
                onChange={({selectedItem}) =>
                  handleFilterChange(
                    'processDefinitionVersion',
                    selectedItem ?? undefined,
                  )
                }
                disabled={!filters.processDefinitionName}
                size="sm"
                style={{width: '100%'}}
                light={true}
              />
              <div style={{width: '100%'}}>
                <FilterableMultiSelect
                  id="process-instance-state"
                  titleText="Process instance state"
                  placeholder="Choose option(s)"
                  items={PROCESS_INSTANCE_STATES}
                  itemToString={(item) => item?.label ?? ''}
                  selectedItems={selectedProcessInstanceStates}
                  onChange={({selectedItems}) =>
                    handleFilterChange(
                      'processInstanceState',
                      selectedItems[0]?.id ?? undefined,
                    )
                  }
                  size="sm"
                  light={true}
                />
              </div>
              <TextInput
                id="process-instance-key"
                labelText="Process instance key"
                placeholder="Filter by instance key"
                value={filters.processInstanceKey || ''}
                onChange={(e) =>
                  handleFilterChange('processInstanceKey', e.target.value)
                }
                size="sm"
                style={{width: '100%'}}
                light={true}
              />
            </Stack>
            <Stack gap={5} orientation="vertical" style={{width: '100%'}}>
              <Title style={{paddingBottom: '0'}}>Operation</Title>
              <div style={{width: '100%'}}>
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
                  light={true}
                />
              </div>
              <div style={{width: '100%'}}>
                <FilterableMultiSelect
                  id="operation-state"
                  titleText="Operation state"
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
                  light={true}
                />
              </div>
              <TextInput
                id="user"
                labelText="Applied by"
                placeholder="Username or client ID"
                value={filters.user || ''}
                onChange={(e) => handleFilterChange('user', e.target.value)}
                size="sm"
                style={{width: '100%'}}
                light={true}
              />
              <DatePicker
                datePickerType="range"
                value={[filters.startDateFrom || '', filters.startDateTo || '']}
                onChange={(dates) => {
                  const [startDate, endDate] = dates;
                  onFiltersChange({
                    ...filters,
                    startDateFrom: startDate?.toISOString() || undefined,
                    startDateTo: endDate?.toISOString() || undefined,
                  });
                }}
                style={{width: '100%'}}
                light={true}
              >
                <DatePickerInput
                  id="start-date"
                  labelText="Start date"
                  placeholder="mm/dd/yyyy"
                  size="sm"
                />
                <DatePickerInput
                  id="end-date"
                  labelText="End date"
                  placeholder="mm/dd/yyyy"
                  size="sm"
                />
              </DatePicker>
            </Stack>
          </Stack>
        </div>
      </Layer>
    );
  },
);

export {BatchOperationsFilters};

