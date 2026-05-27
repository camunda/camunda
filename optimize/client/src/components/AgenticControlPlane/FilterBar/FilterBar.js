/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComboBox} from '@carbon/react';

import {Select} from 'components';

import {DATE_RANGE_OPTIONS, MOCK_PROCESSES} from '../fixtures';

import './FilterBar.scss';

const ALL_PROCESSES_ITEM = {id: '__all__', label: 'All processes'};

// Build item list once; first entry is the "all processes" sentinel object.
const PROCESS_ITEMS = [
  ALL_PROCESSES_ITEM,
  ...MOCK_PROCESSES.map((name) => ({id: name, label: name})),
];

/**
 * Filter bar for the Agentic Control Plane dashboard.
 *
 * - Process selector: Carbon ComboBox populated from MOCK_PROCESSES.
 *   Selecting "All processes" (or clearing) passes null to onProcessChange.
 * - Date range selector: Optimize Select populated from DATE_RANGE_OPTIONS.
 *   Defaults to the value passed in via `dateRangeId`.
 *
 * @param {object}        props
 * @param {string|null}   props.process             - Currently selected process name, or null for all.
 * @param {function}      props.onProcessChange     - Called with the new process name or null.
 * @param {string}        props.dateRangeId         - One of DATE_RANGE_OPTIONS[].id.
 * @param {function}      props.onDateRangeChange   - Called with the new DATE_RANGE_OPTIONS id string.
 */
export default function FilterBar({process, onProcessChange, dateRangeId, onDateRangeChange}) {
  const selectedItem =
    process === null
      ? ALL_PROCESSES_ITEM
      : (PROCESS_ITEMS.find((item) => item.id === process) ?? ALL_PROCESSES_ITEM);

  return (
    <div className="FilterBar">
      <ComboBox
        id="agentic-process-filter"
        className="processFilter"
        titleText="Process"
        placeholder="All processes"
        size="sm"
        items={PROCESS_ITEMS}
        itemToString={(item) => item?.label ?? ''}
        selectedItem={selectedItem}
        onChange={({selectedItem: item}) => {
          if (!item || item.id === '__all__') {
            onProcessChange(null);
          } else {
            onProcessChange(item.id);
          }
        }}
      />
      <Select
        id="agentic-date-range-filter"
        className="dateRangeFilter"
        labelText="Date range"
        value={dateRangeId}
        onChange={onDateRangeChange}
      >
        {DATE_RANGE_OPTIONS.map(({id, label}) => (
          <Select.Option key={id} value={id} label={label} />
        ))}
      </Select>
    </div>
  );
}
