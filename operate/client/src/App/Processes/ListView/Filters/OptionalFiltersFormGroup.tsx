/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useState} from 'react';
import {useLocation, type Location} from 'react-router-dom';
import {type FieldValidator} from 'final-form';
import {Close} from '@carbon/react/icons';
import intersection from 'lodash/intersection';
import {type ProcessInstanceFilterField} from 'modules/utils/filter/shared';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {
  validateIdsCharacters,
  validateIdsLength,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validatesIdsComplete,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {tracking} from 'modules/tracking';
import {OptionalFiltersMenu} from 'modules/components/OptionalFilters';
import {DateRangeField} from 'modules/components/DateRangeField';
import {Field, useForm} from 'react-final-form';
import {Checkbox, IconButton, Stack} from '@carbon/react';
import {TextInputField} from 'modules/components/TextInputField';
import {TextAreaField} from 'modules/components/TextAreaField';
import {
  ButtonContainer,
  FieldContainer,
} from 'modules/components/FiltersPanel/styled';
import {Variable} from './VariableField';

type OptionalFilter =
  | 'variable'
  | 'ids'
  | 'parentInstanceId'
  | 'operationId'
  | 'errorMessage'
  | 'retriesLeft'
  | 'startDateRange'
  | 'endDateRange';

const optionalFilters: Array<OptionalFilter> = [
  'variable',
  'ids',
  'operationId',
  'parentInstanceId',
  'errorMessage',
  'retriesLeft',
  'startDateRange',
  'endDateRange',
];
const OPTIONAL_FILTER_FIELDS: Record<
  OptionalFilter,
  {
    label: string;
    placeholder?: string;
    type?: 'multiline' | 'text' | 'checkbox';
    rows?: number;
    validate?: FieldValidator<string | undefined>;
    keys: ProcessInstanceFilterField[];
  }
> = {
  variable: {
    keys: ['variableName', 'variableValues'],
    label: 'Variable',
  },
  ids: {
    keys: ['ids'],
    label: 'Process Instance Key(s)',
    type: 'multiline',
    placeholder: 'separated by space or comma',
    rows: 1,
    validate: mergeValidators(
      validateIdsCharacters,
      validateIdsLength,
      validatesIdsComplete,
    ),
  },
  operationId: {
    keys: ['operationId'],
    label: 'Operation Id',
    type: 'text',
    validate: mergeValidators(
      validateOperationIdCharacters,
      validateOperationIdComplete,
    ),
  },
  parentInstanceId: {
    keys: ['parentInstanceId'],
    label: 'Parent Process Instance Key',
    type: 'text',
    validate: mergeValidators(
      validateParentInstanceIdComplete,
      validateParentInstanceIdNotTooLong,
      validateParentInstanceIdCharacters,
    ),
  },
  errorMessage: {
    keys: ['errorMessage'],
    label: 'Error Message',
    type: 'text',
  },
  retriesLeft: {
    keys: ['retriesLeft'],
    label: 'Failed job but retries left',
    type: 'checkbox',
  },
  startDateRange: {
    keys: ['startDateAfter', 'startDateBefore'],
    label: 'Start Date Range',
  },
  endDateRange: {
    keys: ['endDateAfter', 'endDateBefore'],
    label: 'End Date Range',
  },
};

type LocationType = Omit<Location, 'state'> & {
  state: {hideOptionalFilters?: boolean};
};

type Props = {
  visibleFilters: OptionalFilter[];
  onVisibleFilterChange: React.Dispatch<React.SetStateAction<OptionalFilter[]>>;
};

const OptionalFiltersFormGroup: React.FC<Props> = observer(
  ({visibleFilters, onVisibleFilterChange}) => {
    const location = useLocation() as LocationType;
    const form = useForm();

    useEffect(() => {
      const filters = getProcessInstanceFilters(location.search);

      onVisibleFilterChange((currentVisibleFilters) => {
        return Array.from(
          new Set([
            ...(location.state?.hideOptionalFilters
              ? []
              : currentVisibleFilters),
            ...([
              ...intersection(Object.keys(filters), optionalFilters),
              ...('startDateAfter' in filters && 'startDateBefore' in filters
                ? ['startDateRange']
                : []),
              ...('endDateAfter' in filters && 'endDateBefore' in filters
                ? ['endDateRange']
                : []),
            ] as OptionalFilter[]),
          ]),
        );
      });
    }, [location.state, location.search, onVisibleFilterChange]);

    const [isStartDateRangeModalOpen, setIsStartDateRangeModalOpen] =
      useState<boolean>(false);
    const [isEndDateRangeModalOpen, setIsEndDateRangeModalOpen] =
      useState<boolean>(false);

    return (
      <Stack gap={8}>
        <OptionalFiltersMenu<OptionalFilter>
          visibleFilters={visibleFilters}
          optionalFilters={optionalFilters.map((id) => ({
            id,
            label: OPTIONAL_FILTER_FIELDS[id].label,
          }))}
          onFilterSelect={(filter) => {
            onVisibleFilterChange(
              Array.from(new Set([...visibleFilters, ...[filter]])),
            );
            tracking.track({
              eventName: 'optional-filter-selected',
              filterName: filter,
            });
            if (filter === 'startDateRange') {
              setIsStartDateRangeModalOpen(true);
            }
            if (filter === 'endDateRange') {
              setIsEndDateRangeModalOpen(true);
            }
          }}
        />
        <Stack gap={5}>
          {visibleFilters.map((filter) => (
            <FieldContainer key={filter}>
              {(() => {
                switch (filter) {
                  case 'variable':
                    return <Variable />;
                  case 'startDateRange':
                    return (
                      <DateRangeField
                        isModalOpen={isStartDateRangeModalOpen}
                        onModalClose={() => setIsStartDateRangeModalOpen(false)}
                        onClick={() => setIsStartDateRangeModalOpen(true)}
                        filterName={filter}
                        popoverTitle="Filter instances by start date"
                        label={OPTIONAL_FILTER_FIELDS[filter].label}
                        fromDateTimeKey="startDateAfter"
                        toDateTimeKey="startDateBefore"
                      />
                    );
                  case 'endDateRange':
                    return (
                      <DateRangeField
                        isModalOpen={isEndDateRangeModalOpen}
                        onModalClose={() => setIsEndDateRangeModalOpen(false)}
                        onClick={() => setIsEndDateRangeModalOpen(true)}
                        filterName={filter}
                        popoverTitle="Filter instances by end date"
                        label={OPTIONAL_FILTER_FIELDS[filter].label}
                        fromDateTimeKey="endDateAfter"
                        toDateTimeKey="endDateBefore"
                      />
                    );
                  default:
                    return (
                      <Field
                        name={filter}
                        validate={OPTIONAL_FILTER_FIELDS[filter].validate}
                        type={OPTIONAL_FILTER_FIELDS[filter].type}
                      >
                        {({input}) => {
                          const field = OPTIONAL_FILTER_FIELDS[filter];

                          if (field.type === 'text') {
                            return (
                              <TextInputField
                                {...input}
                                onChange={(event) => {
                                  if (input.name === 'errorMessage') {
                                    // clear errorMessageHashCode when error message is changed manually.
                                    form.change(
                                      'incidentErrorHashCode',
                                      undefined,
                                    );
                                  }

                                  input.onChange(event);
                                }}
                                id={filter}
                                size="sm"
                                labelText={field.label}
                                placeholder={field.placeholder}
                                autoFocus
                              />
                            );
                          }
                          if (field.type === 'multiline') {
                            return (
                              <TextAreaField
                                {...input}
                                id={filter}
                                labelText={field.label}
                                placeholder={field.placeholder}
                                rows={field.rows}
                                autoFocus
                              />
                            );
                          }
                          if (field.type === 'checkbox') {
                            return (
                              <Checkbox
                                {...input}
                                id={filter}
                                labelText={field.label}
                                autoFocus
                              />
                            );
                          }
                        }}
                      </Field>
                    );
                }
              })()}
              <ButtonContainer>
                <IconButton
                  kind="ghost"
                  label={`Remove ${OPTIONAL_FILTER_FIELDS[filter].label} Filter`}
                  align="top-end"
                  size="sm"
                  onClick={() => {
                    onVisibleFilterChange(
                      visibleFilters.filter(
                        (visibleFilter) => visibleFilter !== filter,
                      ),
                    );

                    OPTIONAL_FILTER_FIELDS[filter].keys.forEach((key) => {
                      form.change(key, undefined);
                      if (key === 'errorMessage') {
                        // clear errorMessageHashCode when error message field is removed.
                        form.change('incidentErrorHashCode', undefined);
                      }
                    });
                    form.submit();
                  }}
                >
                  <Close />
                </IconButton>
              </ButtonContainer>
            </FieldContainer>
          ))}
        </Stack>
      </Stack>
    );
  },
);

export {OptionalFiltersFormGroup};
export type {OptionalFilter};
