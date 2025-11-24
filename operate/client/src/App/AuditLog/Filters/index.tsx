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
import type {
  OperationType,
  OperationState,
  AuditLogSearchFilters,
} from 'modules/api/v2/auditLog/searchAuditLog';
import {observer} from 'mobx-react';
import { Title } from 'modules/components/FiltersPanel/styled';

const OPERATION_TYPES: {id: OperationType; label: string}[] = [
  {id: 'CREATE_PROCESS_INSTANCE', label: 'Create process instance'},
  {id: 'CANCEL_PROCESS_INSTANCE', label: 'Cancel process instance'},
  {id: 'MODIFY_PROCESS_INSTANCE', label: 'Modify process instance'},
  {id: 'MIGRATE_PROCESS_INSTANCE', label: 'Migrate process instance'},
  {id: 'RESOLVE_INCIDENT', label: 'Resolve incident'},
  {id: 'ADD_VARIABLE', label: 'Add variable'},
  {id: 'UPDATE_VARIABLE', label: 'Update variable'},
  {id: 'EVALUATE_DECISION', label: 'Evaluate decision'},
  {id: 'DEPLOY_RESOURCE', label: 'Deploy resource'},
  {id: 'DELETE_RESOURCE', label: 'Delete resource'},
];

const OPERATION_STATES: {id: OperationState; label: string}[] = [
  {id: 'success', label: 'Success'},
  {id: 'fail', label: 'Failed'},
];

// Process definitions with versions - in production, this would come from an API
const PROCESS_DEFINITIONS_WITH_VERSIONS: Record<string, number[]> = {
  'Order Process': [3, 2, 1],
  'Payment Process': [2, 1],
  'Claims Process': [2, 1],
  'Fulfillment Process': [1],
  'Check in process': [1],
  'Process C': [2, 1],
  'Pricing Process': [1],
  'Decision Process': [1],
  'Final process': [1],
  'A test process': [1],
  'B process': [1],
  'another process': [1],
  'testing process': [2, 1],
  'process d': [3, 2, 1],
};

const PROCESS_DEFINITIONS = Object.keys(PROCESS_DEFINITIONS_WITH_VERSIONS);

type AuditLogFiltersProps = {
  filters: AuditLogSearchFilters;
  onFiltersChange: (filters: AuditLogSearchFilters) => void;
};

const AuditLogFilters: React.FC<AuditLogFiltersProps> = observer(
  ({filters, onFiltersChange}) => {
    const handleFilterChange = useCallback(
      (
        key: keyof AuditLogSearchFilters,
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
      return PROCESS_DEFINITIONS_WITH_VERSIONS[filters.processDefinitionName] || [];
    }, [filters.processDefinitionName]);

    // Auto-select latest version when process definition changes
    const handleProcessDefinitionChange = useCallback(
      (selectedProcess: string | null) => {
        if (selectedProcess) {
          const versions = PROCESS_DEFINITIONS_WITH_VERSIONS[selectedProcess];
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

    return (
      <Layer>
        <div style={{padding: 'var(--cds-spacing-05)'}}>
          <Stack gap={8} orientation="vertical" style={{width: '100%'}}>
            <Stack gap={5} orientation="vertical" style={{width: '100%'}}>
              <Title style={{paddingBottom: '0'}}>Process</Title>
              <ComboBox
                id="process-definition-name"
                titleText="Name"
                placeholder="Search by process name"
                items={PROCESS_DEFINITIONS}
                selectedItem={filters.processDefinitionName || null}
                onChange={({selectedItem}) =>
                  handleProcessDefinitionChange(selectedItem ?? null)
                }
                light={true}
                size="sm"
                style={{width: '100%'}}
            />
            <ComboBox
              id="process-definition-version"
              titleText="Version"
              placeholder={
                filters.processDefinitionName ? 'Select version' : 'Select a process version'
              }
              items={availableVersions}
              selectedItem={
                filters.processDefinitionVersion !== undefined
                  ? filters.processDefinitionVersion
                  : null
              }
              onChange={({selectedItem}) =>
                handleFilterChange('processDefinitionVersion', selectedItem ?? undefined)
              }
              disabled={!filters.processDefinitionName}
              light={true}
              size="sm"
              style={{width: '100%'}}
            />
            <TextInput
              id="process-instance-key"
              labelText="Process instance key"
              placeholder="Filter by instance key"
              value={filters.processInstanceKey || ''}
              onChange={(e) =>
                handleFilterChange('processInstanceKey', e.target.value)
              }
              light={true}
              size="sm"
              style={{width: '100%'}}
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
                light={true}
                onChange={({selectedItems}) =>
                  handleFilterChange(
                    'operationType',
                    selectedItems[0]?.id ?? undefined,
                  )
                }
                size="sm"
              />
            </div>
            <div style={{width: '100%'}}>
              <FilterableMultiSelect
                id="operation-state"
                titleText="Operation status"
                placeholder="Choose option(s)"
                items={OPERATION_STATES}
                itemToString={(item) => item?.label ?? ''}
                selectedItems={selectedOperationStates}
                light={true}
                onChange={({selectedItems}) =>
                  handleFilterChange(
                    'operationState',
                    selectedItems[0]?.id ?? undefined,
                  )
                }
                size="sm"
              />
            </div>
            <DatePicker
              datePickerType="range"
              value={[filters.startDateFrom || '', filters.startDateTo || '']}
              onChange={(dates) => {
                const [startDate, endDate] = dates;
                handleFilterChange('startDateFrom', startDate?.toISOString() || '');
                handleFilterChange('startDateTo', endDate?.toISOString() || '');
              }}
              light={true}
              style={{width: '100%'}}
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
            <TextInput
              id="user"
              labelText="Applied by"
              placeholder="Username or client ID"
              value={filters.user || ''}
              onChange={(e) => handleFilterChange('user', e.target.value)}
              light={true}
              size="sm"
              style={{width: '100%'}}
            />
          </Stack>
        </Stack>
        </div>
      </Layer>
    );
  },
);

export {AuditLogFilters};
