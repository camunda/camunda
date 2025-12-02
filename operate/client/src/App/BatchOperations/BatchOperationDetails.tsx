/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo, useEffect, useCallback} from 'react';
import {useParams, useNavigate, useLocation} from 'react-router-dom';
import React from 'react';
import {
  Button,
  Stack,
  InlineNotification,
  Link,
  Pagination,
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableExpandRow,
  TableExpandedRow,
  TableExpandHeader,
  TableContainer,
  OverflowMenu,
  OverflowMenuItem,
  Breadcrumb,
  BreadcrumbItem,
  Tooltip,
  TextInput,
  FilterableMultiSelect,
  Layer,
  DatePicker,
  DatePickerInput,
  Modal,
} from '@carbon/react';
import {
  Pause,
  Play,
  Close,
  Checkmark,
  Error,
  CircleDash,
  Filter,
} from '@carbon/icons-react';
import {Calendar} from '@carbon/react/icons';
import {createPortal} from 'react-dom';
import {format} from 'date-fns';
import {IconTextInput} from 'modules/components/IconInput';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {PAGE_TITLE} from 'modules/constants';
import {
  mockBatchOperations,
  type BatchOperationItemState,
  type BatchOperationError,
} from 'modules/mocks/batchOperations';
import {BatchOperationStatusTag} from './BatchOperationStatusTag';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Paths} from 'modules/Routes';
import {IS_BATCH_OPERATIONS_FILTERING_ENABLED} from 'modules/feature-flags';

// Item state options for filter
const ITEM_STATES: {id: BatchOperationItemState; label: string}[] = [
  {id: 'ACTIVE', label: 'Active'},
  {id: 'COMPLETED', label: 'Completed'},
  {id: 'FAILED', label: 'Failed'},
  {id: 'SKIPPED', label: 'Skipped'},
  {id: 'CANCELLED', label: 'Cancelled'},
];

const DEFAULT_FROM_TIME = '00:00:00';
const DEFAULT_TO_TIME = '23:59:59';

const formatDateForDisplay = (date: Date) => format(date, 'yyyy-MM-dd');
const formatTimeForDisplay = (date: Date) => format(date, 'HH:mm:ss');

// Validate time input format (hh:mm:ss)
const isValidTimeFormat = (time: string) => {
  if (!time) return false;
  const timeRegex = /^([0-1]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$/;
  return timeRegex.test(time);
};

// Date-time range field component for filtering
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

  const validateTime = (time: string) => {
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
    setFromTimeError(validateTime(value));
  };

  const handleToTimeChange = (value: string) => {
    setToTime(value);
    setToTimeError(validateTime(value));
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
        placeholder="Select date range"
        size="sm"
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

// Items filter type
type ItemsFilters = {
  processName?: string;
  processInstanceKeys?: string;
  states?: BatchOperationItemState[];
  timeFrom?: string;
  timeTo?: string;
};


// Reusable Summary Tile component
const SummaryTile: React.FC<{
  label: string;
  children: React.ReactNode;
}> = ({label, children}) => (
  <div
    style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--cds-spacing-03)',
      alignItems: 'flex-start',
      padding: 'var(--cds-spacing-05)',
      backgroundColor: 'var(--cds-layer-01)',
      border: '1px solid var(--cds-border-subtle)',
      minWidth: '200px',
      fontSize: 'var(--cds-font-size-03)',
    }}
  >
    <span
      style={{
        fontSize: '0.75rem',
        color: 'var(--cds-text-secondary)',
      }}
    >
      {label}
    </span>
    {children}
  </div>
);

const Subtitle: React.FC<{children: React.ReactNode}> = ({children}) => (
  <h5 style={{fontWeight: 600, marginBottom: 'var(--cds-spacing-03)'}}>
    {children}
  </h5>
);

const formatOperationType = (type: string) => {
  return type
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
};

const BatchOperationDetails: React.FC = () => {
  const {operationId} = useParams<{operationId: string}>();
  const navigate = useNavigate();
  const location = useLocation();
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [showFilters, setShowFilters] = useState(false);
  const [itemFilters, setItemFilters] = useState<ItemsFilters>({});

  // Get sort from URL or use defaults
  const sortParams = getSortParams(location.search);
  const sortBy = sortParams?.sortBy || 'time';
  const sortOrder = sortParams?.sortOrder?.toUpperCase() || 'DESC';

  // Find the operation from mock data
  const operation = useMemo(() => {
    return mockBatchOperations.find((op) => op.id === operationId) || null;
  }, [operationId]);

  // Handle filter changes
  const handleFilterChange = useCallback(
    (key: keyof ItemsFilters, value: string | BatchOperationItemState[] | undefined) => {
      setItemFilters((prev) => ({...prev, [key]: value}));
      setCurrentPage(1); // Reset to first page when filters change
    },
    [],
  );

  // Get selected states for the multi-select
  const selectedStates = useMemo(
    () =>
      itemFilters.states?.length
        ? ITEM_STATES.filter((s) => itemFilters.states!.includes(s.id))
        : [],
    [itemFilters.states],
  );

  // Check if any filters are active
  const hasActiveFilters = useMemo(() => {
    return !!(
      itemFilters.processName ||
      itemFilters.processInstanceKeys ||
      (itemFilters.states && itemFilters.states.length > 0) ||
      itemFilters.timeFrom ||
      itemFilters.timeTo
    );
  }, [itemFilters]);

  // Clear all filters
  const clearFilters = useCallback(() => {
    setItemFilters({});
    setCurrentPage(1);
  }, []);

  useEffect(() => {
    if (operation) {
      document.title = `${PAGE_TITLE.BATCH_OPERATIONS} - ${formatOperationType(operation.operationType)}`;
    } else {
      document.title = PAGE_TITLE.BATCH_OPERATIONS;
    }
  }, [operation]);

  // Filter items
  const filteredItems = useMemo(() => {
    if (!operation) {
      return [];
    }

    let items = [...operation.items];

    // Filter by process name
    if (itemFilters.processName) {
      const searchTerm = itemFilters.processName.toLowerCase();
      items = items.filter((item) =>
        item.processDefinitionName.toLowerCase().includes(searchTerm),
      );
    }

    // Filter by process instance key(s)
    if (itemFilters.processInstanceKeys) {
      const keys = itemFilters.processInstanceKeys
        .split(',')
        .map((k) => k.trim())
        .filter((k) => k.length > 0);
      if (keys.length > 0) {
        items = items.filter((item) =>
          keys.some((key) => item.processInstanceKey.includes(key)),
        );
      }
    }

    // Filter by states
    if (itemFilters.states && itemFilters.states.length > 0) {
      items = items.filter((item) => itemFilters.states!.includes(item.state));
    }

    // Filter by time range
    if (itemFilters.timeFrom) {
      const fromTime = new Date(itemFilters.timeFrom).getTime();
      items = items.filter((item) => new Date(item.timestamp).getTime() >= fromTime);
    }
    if (itemFilters.timeTo) {
      const toTime = new Date(itemFilters.timeTo).getTime();
      items = items.filter((item) => new Date(item.timestamp).getTime() <= toTime);
    }

    return items;
  }, [operation, itemFilters]);

  // Sort and paginate items
  const sortedAndPaginatedItems = useMemo(() => {
    if (filteredItems.length === 0) {
      return [];
    }

    // First sort the items
    const sortedItems = [...filteredItems].sort((a, b) => {
      let comparison = 0;
      switch (sortBy) {
        case 'instanceName':
          comparison = a.processDefinitionName.localeCompare(
            b.processDefinitionName,
          );
          break;
        case 'key':
          comparison = a.processInstanceKey.localeCompare(b.processInstanceKey);
          break;
        case 'state':
          comparison = a.state.localeCompare(b.state);
          break;
        case 'time':
          comparison =
            new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();
          break;
        default:
          return 0;
      }
      return sortOrder === 'DESC' ? comparison : -comparison;
    });

    // Then paginate
    const startIndex = (currentPage - 1) * pageSize;
    return sortedItems.slice(startIndex, startIndex + pageSize);
  }, [filteredItems, sortBy, sortOrder, currentPage, pageSize]);

  const itemsTableRows = useMemo(
    () =>
      sortedAndPaginatedItems.map((item) => ({
        id: item.id,
        instanceName: item.processDefinitionName,
        instanceKey: item.processInstanceKey,
        state: item.state,
        time: formatDate(item.timestamp),
        errorMessage: item.errorMessage,
      })),
    [sortedAndPaginatedItems],
  );

  const itemsTableHeaders = [
    {key: 'instanceName', header: 'Process name'},
    {key: 'instanceKey', header: 'Process instance key'},
    {key: 'state', header: 'Operation state'},
    {key: 'time', header: 'Time'},
  ];

  // Handle sort click
  const handleSort = (headerKey: string) => {
    const newParams = new URLSearchParams(location.search);
    const currentSort = getSortParams(location.search);

    if (headerKey === currentSort?.sortBy) {
      const newOrder = currentSort.sortOrder === 'asc' ? 'desc' : 'asc';
      newParams.set('sort', `${headerKey}+${newOrder}`);
    } else {
      newParams.set('sort', `${headerKey}+desc`);
    }

    navigate({search: newParams.toString()}, {replace: true});
  };

  if (!operation) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          backgroundColor: 'var(--cds-layer)',
        }}
      >
        {/* Breadcrumb - Full Width */}
        <div
          style={{
            width: '100%',
            backgroundColor: 'var(--cds-layer-01)',
            borderBottom: '1px solid var(--cds-border-subtle-01)',
            padding: 'var(--cds-spacing-04) var(--cds-spacing-05)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
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
            <BreadcrumbItem>
              <Link
                href="#"
                onClick={(e: React.MouseEvent) => {
                  e.preventDefault();
                  navigate(Paths.batchOperations());
                }}
              >
                Batch Operations
              </Link>
            </BreadcrumbItem>
            <BreadcrumbItem isCurrentPage>Not found</BreadcrumbItem>
          </Breadcrumb>
        </div>
        <div style={{padding: 'var(--cds-spacing-05)'}}>
          <InlineNotification
            kind="error"
            title="Operation not found"
            subtitle={`The batch operation with ID "${operationId}" could not be found.`}
            hideCloseButton
            lowContrast
          />
        </div>
      </div>
    );
  }

  return (
    <>
      <VisuallyHiddenH1>
        {formatOperationType(operation.operationType)}
      </VisuallyHiddenH1>
      {/* Breadcrumb - Full Width */}
      <div
        style={{
          width: '100%',
          backgroundColor: 'var(--cds-layer-01)',
          borderBottom: '1px solid var(--cds-border-subtle-01)',
          padding: 'var(--cds-spacing-04) var(--cds-spacing-05)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
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
          <BreadcrumbItem>
            <Link
              href="#"
              onClick={(e: React.MouseEvent) => {
                e.preventDefault();
                navigate(Paths.batchOperations());
              }}
            >
              Batch Operations
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isCurrentPage>
            {formatOperationType(operation.operationType)}
          </BreadcrumbItem>
        </Breadcrumb>
      </div>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: 'calc(100% - 48px)',
          backgroundColor: 'var(--cds-layer)',
          overflow: 'hidden',
        }}
      >
        {/* Title with Actions */}
        <div
          style={{
            padding: 'var(--cds-spacing-05)',
            paddingBottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <h3>{formatOperationType(operation.operationType)}</h3>

          {/* Action buttons based on state */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--cds-spacing-03)',
            }}
          >
            {operation.state === 'ACTIVE' && (
              <Button
                kind="tertiary"
                size="md"
                renderIcon={Pause}
                onClick={() => {
                  // TODO: Implement suspend functionality
                  console.log('Suspend operation:', operation.id);
                }}
              >
                Suspend
              </Button>
            )}

            {operation.state === 'SUSPENDED' && (
              <Button
                kind="tertiary"
                size="md"
                renderIcon={Play}
                onClick={() => {
                  // TODO: Implement resume functionality
                  console.log('Resume operation:', operation.id);
                }}
              >
                Resume
              </Button>
            )}

            {(operation.state === 'CREATED' ||
              operation.state === 'ACTIVE' ||
              operation.state === 'SUSPENDED') && (
              <OverflowMenu
                size="md"
                align="bottom"
                aria-label="Additional options"
              >
                <OverflowMenuItem
                  itemText="Cancel"
                  isDelete
                  onClick={() => {
                    // TODO: Implement cancel functionality
                    console.log('Cancel operation:', operation.id);
                  }}
                />
              </OverflowMenu>
            )}
            <Button
              kind="ghost"
              size="md"
              hasIconOnly
              renderIcon={Close}
              iconDescription="Close"
              tooltipPosition="bottom"
              onClick={() => navigate(Paths.batchOperations())}
            />
          </div>
        </div>

        {/* Content */}
        <div
          style={{
            flex: 1,
            overflow: 'auto',
            padding: 'var(--cds-spacing-05)',
          }}
        >
          <Stack gap={6}>
            {/* Summary Section - Separate tiles in one row */}
            <Stack gap={4}>
              <style>{`
                .batch-operation-summary .cds--popover-content {
                  padding: var(--cds-spacing-02) var(--cds-spacing-03);
                  font-size: var(--cds-body-compact-01-font-size);
                  max-width: 150px;
                }
              `}</style>
              <div
                className="batch-operation-summary"
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 'var(--cds-spacing-04)',
                }}
              >
                <SummaryTile label="State">
                  <BatchOperationStatusTag status={operation.state} />
                </SummaryTile>

                <SummaryTile label="Summary of items">
                  {(() => {
                    const successCount = operation.completedItems;
                    const failedCount = operation.failedItems;
                    const pendingCount = operation.totalItems - successCount - failedCount;
                    const hasAnyProgress = successCount > 0 || failedCount > 0;

                    if (!hasAnyProgress && pendingCount > 0) {
                      return (
                        <Tooltip description={`${pendingCount} not started`} align="bottom">
                          <span style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)', color: 'var(--cds-text-secondary)', cursor: 'default', fontWeight: 500}}>
                            <CircleDash size={16} />
                            {pendingCount}
                          </span>
                        </Tooltip>
                      );
                    }

                    return (
                      <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)', fontWeight: 500}}>
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
                  })()}
                </SummaryTile>

                <SummaryTile label="Start time">
                  <span style={{fontWeight: 500}}>
                    {formatDate(operation.startTime)}
                  </span>
                </SummaryTile>

                <SummaryTile label="End time">
                  <span style={{fontWeight: 500}}>
                    {operation.endTime ? formatDate(operation.endTime) : '-'}
                  </span>
                </SummaryTile>

                <SummaryTile label="Applied by">
                  <span style={{fontWeight: 500}}>{operation.appliedBy}</span>
                </SummaryTile>
              </div>

              {/* Error messages for failed operations */}
              {operation.state === 'FAILED' && operation.errors && operation.errors.length > 0 && (
                <>
                  <style>{`
                    .batch-operation-error-notification .cds--inline-notification__text-wrapper {
                      width: 100%;
                      display: flex;
                      flex-direction: column;
                    }
                    .batch-operation-error-notification .cds--inline-notification__subtitle {
                      margin-top: var(--cds-spacing-02);
                      width: 100%;
                    }
                  `}</style>
                  <div style={{width: '100%'}}>
                    <InlineNotification
                      className="batch-operation-error-notification"
                      kind="error"
                      title={operation.errors.length > 1 ? 'Multiple errors occured' : `Error occured`}
                      subtitle={
                        operation.errors.length > 0 ? (
                          <div style={{display: 'flex', flexDirection: 'column', gap: 'var(--cds-spacing-02)'}}>
                            {operation.errors.map((error, index) => (
                              <div key={index}>
                                {error.type}: {error.message}
                              </div>
                            ))}
                          </div>
                        ) : (
                          operation.errors[0].message
                        )
                      }
                      hideCloseButton
                      lowContrast
                      style={{maxWidth: 'unset', width: '100%'}}
                    />
                  </div>
                </>
              )}
            </Stack>

            {/* Items Section */}
            <Stack gap={2}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  minHeight: '40px',
                }}
              >
                <span style={{fontWeight: 600}}>
                  {IS_BATCH_OPERATIONS_FILTERING_ENABLED && hasActiveFilters
                    ? `${filteredItems.length} of ${operation.totalItems} item${operation.totalItems !== 1 ? 's' : ''}`
                    : `${operation.totalItems} item${operation.totalItems !== 1 ? 's' : ''}`}
                </span>
                {IS_BATCH_OPERATIONS_FILTERING_ENABLED && (
                  <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-03)'}}>
                    {hasActiveFilters && (
                      <Button
                        kind="ghost"
                        size="md"
                        onClick={clearFilters}
                      >
                        Clear filters
                      </Button>
                    )}
                    <Button
                      kind='ghost'
                      size="sm"
                      renderIcon={Filter}
                      iconDescription="Toggle filters"
                      tooltipPosition="bottom"
                      onClick={() => setShowFilters(!showFilters)}
                    >
                      {showFilters ? "Hide filters" : "Show filters"}
                    </Button>
                  </div>
                )}
              </div>

              {/* Items Filters */}
              {IS_BATCH_OPERATIONS_FILTERING_ENABLED && showFilters && (
                <Layer>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(4, 1fr)',
                      gap: 'var(--cds-spacing-05)',
                      padding: 'var(--cds-spacing-02)',
                    }}
                  >
                    <TextInput
                      id="item-filter-process-name"
                      labelText="Process name"
                      placeholder="Search by name"
                      value={itemFilters.processName || ''}
                      onChange={(e) =>
                        handleFilterChange('processName', e.target.value || undefined)
                      }
                      size="sm"
                    />
                    <TextInput
                      id="item-filter-keys"
                      labelText="Process instance key(s)"
                      placeholder="Keys separated by commas"
                      value={itemFilters.processInstanceKeys || ''}
                      onChange={(e) =>
                        handleFilterChange('processInstanceKeys', e.target.value || undefined)
                      }
                      size="sm"
                    />
                    <FilterableMultiSelect
                      id="item-filter-state"
                      titleText="Operation state"
                      placeholder="All states"
                      items={ITEM_STATES}
                      itemToString={(item) => item?.label ?? ''}
                      selectedItems={selectedStates}
                      onChange={({selectedItems}) =>
                        handleFilterChange(
                          'states',
                          selectedItems.length
                            ? selectedItems.map((item) => item.id)
                            : undefined,
                        )
                      }
                      size="sm"
                    />
                    <DateTimeRangeField
                      id="item-filter-time"
                      label="Time"
                      modalTitle="Filter items by time"
                      fromDateTime={itemFilters.timeFrom}
                      toDateTime={itemFilters.timeTo}
                      onChange={(from, to) => {
                        setItemFilters((prev) => ({
                          ...prev,
                          timeFrom: from,
                          timeTo: to,
                        }));
                        setCurrentPage(1);
                      }}
                    />
                  </div>
                </Layer>
              )}

              <DataTable
                rows={itemsTableRows}
                headers={itemsTableHeaders}
                isSortable
                render={({
                  rows,
                  headers,
                  getTableProps,
                  getHeaderProps,
                  getRowProps,
                  getTableContainerProps,
                }) => (
                  <TableContainer {...getTableContainerProps()}>
                    <Table {...getTableProps()} size="md">
                      <TableHead>
                        <TableRow>
                          <TableExpandHeader aria-label="expand row" />
                          {headers.map((header) => {
                            const headerProps = getHeaderProps({header});
                            return (
                              <TableHeader
                                key={header.key}
                                isSortable
                                isSortHeader={sortBy === header.key}
                                sortDirection={
                                  sortBy === header.key
                                    ? sortOrder === 'ASC'
                                      ? 'ASC'
                                      : 'DESC'
                                    : 'NONE'
                                }
                                onClick={() => handleSort(header.key)}
                              >
                                {header.header}
                              </TableHeader>
                            );
                          })}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((row) => {
                          const rowData = itemsTableRows.find(
                            (r) => r.id === row.id,
                          );
                          const hasError = rowData?.state === 'FAILED';
                          const {key, ...rowProps} = getRowProps({row});

                          const renderCells = () =>
                            row.cells.map((cell: any) => (
                              <TableCell key={cell.id}>
                                {cell.info.header === 'state' ? (
                                  <BatchOperationStatusTag status={cell.value} />
                                ) : cell.info.header === 'instanceKey' ? (
                                  <Link
                                    href="#"
                                    onClick={(e: React.MouseEvent) =>
                                      e.preventDefault()
                                    }
                                  >
                                    {cell.value}
                                  </Link>
                                ) : (
                                  cell.value
                                )}
                              </TableCell>
                            ));

                          // Only show expandable row for failed items
                          if (hasError) {
                            return (
                              <React.Fragment key={key}>
                                <TableExpandRow {...rowProps}>
                                  {renderCells()}
                                </TableExpandRow>
                                <TableExpandedRow colSpan={headers.length + 1}>
                                  <div>
                                    <strong>Failure reason: </strong>
                                    {rowData?.errorMessage || 'Operation failed'}
                                  </div>
                                </TableExpandedRow>
                              </React.Fragment>
                            );
                          }

                          // Regular row for non-failed items
                          return (
                            <TableRow key={key} {...rowProps}>
                              {/* Empty cell for expand column alignment */}
                              <TableCell />
                              {renderCells()}
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              />

              <Pagination
                page={currentPage}
                pageSize={pageSize}
                pageSizes={[20, 50, 100]}
                totalItems={filteredItems.length}
                onChange={({page, pageSize: newPageSize}) => {
                  setCurrentPage(page);
                  setPageSize(newPageSize);
                }}
              />
            </Stack>
          </Stack>
        </div>
      </div>
    </>
  );
};

export {BatchOperationDetails};

