/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Dropdown, DatePicker, DatePickerInput} from '@carbon/react';
import {FiltersContainer, FilterRow, FilterGroup} from 'App/AuditLog/Filters/styled';
import {
  mockProcessDefinitions,
  mockVersions,
  mockOperationTypes,
  mockOperationStatuses,
} from '../mocks';

const AuditLogFilters: React.FC = observer(() => {
  return (
    <FiltersContainer>
      <FilterRow>
        <FilterGroup>
          <Dropdown
            id="process-definition"
            titleText="Process definition"
            label="All processes"
            items={mockProcessDefinitions}
            itemToString={(item) => item || ''}
            selectedItem={mockProcessDefinitions[0]}
            size="sm"
          />
        </FilterGroup>
        
        <FilterGroup>
          <Dropdown
            id="version"
            titleText="Version"
            label="All"
            items={mockVersions}
            itemToString={(item) => item || ''}
            selectedItem={mockVersions[0]}
            size="sm"
          />
        </FilterGroup>
        
        <FilterGroup>
          <Dropdown
            id="operation-type"
            titleText="Operation type"
            label="Choose option(s)"
            items={mockOperationTypes}
            itemToString={(item) => item || ''}
            selectedItem={mockOperationTypes[0]}
            size="sm"
          />
        </FilterGroup>
        
        <FilterGroup>
          <Dropdown
            id="operation-status"
            titleText="Operation status"
            label="Choose option(s)"
            items={mockOperationStatuses}
            itemToString={(item) => item || ''}
            selectedItem={mockOperationStatuses[0]}
            size="sm"
          />
        </FilterGroup>
        
        <FilterGroup>
          <DatePicker datePickerType="single">
            <DatePickerInput
              id="from-date"
              placeholder="mm/dd/yyyy"
              labelText="From"
              size="sm"
            />
          </DatePicker>
        </FilterGroup>
        
        <FilterGroup>
          <DatePicker datePickerType="single">
            <DatePickerInput
              id="to-date"
              placeholder="mm/dd/yyyy"
              labelText="To"
              size="sm"
            />
          </DatePicker>
        </FilterGroup>
      </FilterRow>
    </FiltersContainer>
  );
});

export {AuditLogFilters};
