/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useState} from 'react';
import {
  Stack,
  TextInput,
  FilterableMultiSelect,
  ComboBox,
  Layer,
  DatePicker,
  DatePickerInput,
  Modal,
} from '@carbon/react';
import {Calendar} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {createPortal} from 'react-dom';
import {Title} from 'modules/components/FiltersPanel/styled';
import {IconTextInput} from 'modules/components/IconInput';
import {format} from 'date-fns';
import type {
  BatchOperationType,
  BatchOperationState,
} from 'modules/mocks/batchOperations';

const DEFAULT_FROM_TIME = '00:00:00';
const DEFAULT_TO_TIME = '23:59:59';

const formatDateForDisplay = (date: Date) => format(date, 'yyyy-MM-dd');
const formatTimeForDisplay = (date: Date) => format(date, 'HH:mm:ss');

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

// Validate time input format (hh:mm:ss)
const isValidTimeFormat = (time: string) => {
  if (!time) return false;
  const timeRegex = /^([0-1]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$/;
  return timeRegex.test(time);
};

// Component for displaying the date-time range input with modal
type DateTimeRangeFieldProps = {
  id: string;
  label: string;
  modalTitle: string;
  fromDateTime?: string;
  toDateTime?: string;
  onChange: (fromDateTime: string | undefined, toDateTime: string | undefined) => void;
};

const DateTimeRangeField: React.FC<DateTimeRangeFieldProps> = ({
  id,
  label,
  modalTitle,
  fromDateTime,
  toDateTime,
  onChange,
}) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [fromTime, setFromTime] = useState('');
  const [toTime, setToTime] = useState('');
  const [fromTimeError, setFromTimeError] = useState<string | undefined>();
  const [toTimeError, setToTimeError] = useState<string | undefined>();

  const getInputDisplayValue = () => {
    if (isModalOpen) return 'Custom';
    if (fromDateTime && toDateTime) {
      const fromDt = new Date(fromDateTime);
      const toDt = new Date(toDateTime);
      return `${formatDateForDisplay(fromDt)} ${formatTimeForDisplay(fromDt)} - ${formatDateForDisplay(toDt)} ${formatTimeForDisplay(toDt)}`;
    }
    return '';
  };

  const handleOpenModal = () => {
    // Initialize modal fields from current values
    if (fromDateTime) {
      const date = new Date(fromDateTime);
      setFromDate(formatDateForDisplay(date));
      setFromTime(formatTimeForDisplay(date));
    } else {
      setFromDate('');
      setFromTime('');
    }
    if (toDateTime) {
      const date = new Date(toDateTime);
      setToDate(formatDateForDisplay(date));
      setToTime(formatTimeForDisplay(date));
    } else {
      setToDate('');
      setToTime('');
    }
    setFromTimeError(undefined);
    setToTimeError(undefined);
    setIsModalOpen(true);
  };

  const handleCancel = () => {
    setIsModalOpen(false);
  };

  const handleReset = () => {
    setFromDate('');
    setToDate('');
    setFromTime('');
    setToTime('');
    setFromTimeError(undefined);
    setToTimeError(undefined);
    onChange(undefined, undefined);
    setIsModalOpen(false);
  };

  const validateTime = (time: string, type: 'from' | 'to') => {
    if (!time) {
      return undefined;
    }
    if (!isValidTimeFormat(time)) {
      return 'Please enter a valid time (hh:mm:ss)';
    }
    return undefined;
  };

  const handleFromTimeChange = (value: string) => {
    setFromTime(value);
    setFromTimeError(validateTime(value, 'from'));
  };

  const handleToTimeChange = (value: string) => {
    setToTime(value);
    setToTimeError(validateTime(value, 'to'));
  };

  const handleApply = () => {
    if (fromDate && fromTime && toDate && toTime) {
      const fromDt = new Date(`${fromDate} ${fromTime}`);
      const toDt = new Date(`${toDate} ${toTime}`);
      onChange(fromDt.toISOString(), toDt.toISOString());
      setIsModalOpen(false);
    }
  };

  const isApplyDisabled = 
    !fromDate || !toDate || !fromTime || !toTime || 
    !!fromTimeError || !!toTimeError;

  return (
    <>
      <IconTextInput
        Icon={Calendar}
        id={id}
        labelText={label}
        value={getInputDisplayValue()}
        title={getInputDisplayValue()}
        placeholder="Enter date range"
        size="sm"
        light={true}
        buttonLabel="Open date range modal"
        onIconClick={handleOpenModal}
        onClick={handleOpenModal}
      />

      {isModalOpen && createPortal(
        <Layer level={0}>
          <Modal
            data-testid={`${id}-modal`}
            open={isModalOpen}
            size="sm"
            modalHeading={modalTitle}
            primaryButtonText="Apply"
            secondaryButtons={[
              {
                buttonText: 'Reset',
                onClick: handleReset,
              },
              {
                buttonText: 'Cancel',
                onClick: handleCancel,
              },
            ]}
            onRequestClose={handleCancel}
            onRequestSubmit={handleApply}
            primaryButtonDisabled={isApplyDisabled}
          >
            <Stack gap={6}>
              <div>
                <DatePicker
                  datePickerType="range"
                  onChange={(dates) => {
                    const [from, to] = dates;
                    if (from) {
                      setFromDate(formatDateForDisplay(from));
                      if (!fromTime) setFromTime(DEFAULT_FROM_TIME);
                    }
                    if (to) {
                      setToDate(formatDateForDisplay(to));
                      if (!toTime) setToTime(DEFAULT_TO_TIME);
                    }
                  }}
                  dateFormat="Y-m-d"
                  short
                >
                  <DatePickerInput
                    id={`${id}-from-date`}
                    labelText="From date"
                    placeholder="YYYY-MM-DD"
                    size="sm"
                    value={fromDate}
                    onChange={(e) => setFromDate(e.target.value)}
                    maxLength={10}
                    autoComplete="off"
                  />
                  <DatePickerInput
                    id={`${id}-to-date`}
                    labelText="To date"
                    placeholder="YYYY-MM-DD"
                    size="sm"
                    value={toDate}
                    onChange={(e) => setToDate(e.target.value)}
                    maxLength={10}
                    autoComplete="off"
                  />
                </DatePicker>
              </div>
              <div style={{display: 'flex', gap: '1px', width: '289px'}}>
                <TextInput
                  id={`${id}-from-time`}
                  labelText="From time"
                  placeholder="hh:mm:ss"
                  size="sm"
                  value={fromTime}
                  onChange={(e) => handleFromTimeChange(e.target.value)}
                  data-testid={`${id}-fromTime`}
                  maxLength={8}
                  autoComplete="off"
                  invalid={!!fromTimeError}
                  invalidText={fromTimeError}
                />
                <TextInput
                  id={`${id}-to-time`}
                  labelText="To time"
                  placeholder="hh:mm:ss"
                  size="sm"
                  value={toTime}
                  onChange={(e) => handleToTimeChange(e.target.value)}
                  data-testid={`${id}-toTime`}
                  maxLength={8}
                  autoComplete="off"
                  invalid={!!toTimeError}
                  invalidText={toTimeError}
                />
              </div>
            </Stack>
          </Modal>
        </Layer>,
        document.body
      )}
    </>
  );
};

export type BatchOperationsFilters = {
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  processInstanceState?: string;
  processInstanceKey?: string;
  operationTypes?: BatchOperationType[];
  operationStates?: BatchOperationState[];
  startDateFrom?: string;
  startDateTo?: string;
  endDateFrom?: string;
  endDateTo?: string;
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
        filters.operationTypes?.length
          ? OPERATION_TYPES.filter((t) => filters.operationTypes!.includes(t.id))
          : [],
      [filters.operationTypes],
    );

    const selectedOperationStates = useMemo(
      () =>
        filters.operationStates?.length
          ? OPERATION_STATES.filter((s) => filters.operationStates!.includes(s.id))
          : [],
      [filters.operationStates],
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
                placeholder="Search by process name"
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
                    : 'Select process version'
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
                    onFiltersChange({
                      ...filters,
                      operationTypes: selectedItems.length
                        ? selectedItems.map((item) => item.id)
                        : undefined,
                    })
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
                    onFiltersChange({
                      ...filters,
                      operationStates: selectedItems.length
                        ? selectedItems.map((item) => item.id)
                        : undefined,
                    })
                  }
                  size="sm"
                  light={true}
                />
              </div>
              <TextInput
                id="user"
                labelText="Actor"
                placeholder="Username or client ID"
                value={filters.user || ''}
                onChange={(e) => handleFilterChange('user', e.target.value)}
                size="sm"
                style={{width: '100%'}}
                light={true}
              />
              <DateTimeRangeField
                id="start-date-range"
                label="Start date range"
                modalTitle="Filter operations by start date"
                fromDateTime={filters.startDateFrom}
                toDateTime={filters.startDateTo}
                onChange={(from, to) => {
                  onFiltersChange({
                    ...filters,
                    startDateFrom: from,
                    startDateTo: to,
                  });
                }}
              />
              <DateTimeRangeField
                id="end-date-range"
                label="End date range"
                modalTitle="Filter operations by end date"
                fromDateTime={filters.endDateFrom}
                toDateTime={filters.endDateTo}
                onChange={(from, to) => {
                  onFiltersChange({
                    ...filters,
                    endDateFrom: from,
                    endDateTo: to,
                  });
                }}
              />
            </Stack>
          </Stack>
        </div>
      </Layer>
    );
  },
);

export {BatchOperationsFilters};

