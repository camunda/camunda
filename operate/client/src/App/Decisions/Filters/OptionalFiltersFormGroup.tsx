/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useState} from 'react';
import {useLocation, useSearchParams, type Location} from 'react-router-dom';
import type {FieldValidator} from 'final-form';
import {
  parseDecisionsFilter,
  type DecisionsFilterField,
} from 'modules/utils/filter/decisionsFilter';
import {
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validatesDecisionIdsComplete,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {tracking} from 'modules/tracking';
import {OptionalFiltersMenu} from 'modules/components/OptionalFilters';
import {DateRangeField} from 'modules/components/DateRangeField';
import {Field, useForm} from 'react-final-form';
import {IconButton, Stack} from '@carbon/react';
import {TextInputField} from 'modules/components/TextInputField';
import {TextAreaField} from 'modules/components/TextAreaField';
import {
  ButtonContainer,
  FieldContainer,
} from 'modules/components/FiltersPanel/styled';
import {Close} from '@carbon/react/icons';

type OptionalFilter =
  | 'decisionInstanceIds'
  | 'processInstanceId'
  | 'evaluationDateRange';

const optionalFilters: Array<OptionalFilter> = [
  'decisionInstanceIds',
  'processInstanceId',
  'evaluationDateRange',
];

const OPTIONAL_FILTER_FIELDS: Record<
  OptionalFilter,
  {
    label: string;
    placeholder?: string;
    type?: 'multiline' | 'text';
    rows?: number;
    validate?: FieldValidator<string | undefined>;
    keys: DecisionsFilterField[];
  }
> = {
  decisionInstanceIds: {
    keys: ['decisionInstanceIds'],
    label: 'Decision Instance Key(s)',
    type: 'multiline',
    placeholder: 'Separated by space or comma',
    rows: 1,
    validate: mergeValidators(
      validateDecisionIdsCharacters,
      validateDecisionIdsLength,
      validatesDecisionIdsComplete,
    ),
  },
  processInstanceId: {
    keys: ['processInstanceId'],
    label: 'Process Instance Key',
    type: 'text',
    validate: mergeValidators(
      validateParentInstanceIdComplete,
      validateParentInstanceIdNotTooLong,
      validateParentInstanceIdCharacters,
    ),
  },
  evaluationDateRange: {
    keys: ['evaluationDateAfter', 'evaluationDateBefore'],
    label: 'Evaluation Date Range',
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
    const [params] = useSearchParams();
    const form = useForm();

    useEffect(() => {
      if (location.state?.hideOptionalFilters) {
        onVisibleFilterChange([]);
      }
    }, [location.state, onVisibleFilterChange]);

    useEffect(() => {
      const optionalParams = Array.from(params.keys()).filter((param) =>
        (optionalFilters as string[]).includes(param),
      ) as OptionalFilter[];

      const filters = parseDecisionsFilter(params);

      onVisibleFilterChange((currentVisibleFilters) =>
        Array.from(
          new Set([
            ...currentVisibleFilters,
            ...optionalParams,
            ...('evaluationDateAfter' in filters &&
            'evaluationDateBefore' in filters
              ? ['evaluationDateRange']
              : []),
          ] as OptionalFilter[]),
        ),
      );
    }, [params, onVisibleFilterChange]);

    const [isDateRangeModalOpen, setIsDateRangeModalOpen] =
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
            if (filter === 'evaluationDateRange') {
              setTimeout(() => {
                setIsDateRangeModalOpen(true);
              });
            }
          }}
        />
        <Stack gap={5}>
          {visibleFilters.map((filter) => (
            <FieldContainer key={filter}>
              {filter === 'evaluationDateRange' ? (
                <DateRangeField
                  isModalOpen={isDateRangeModalOpen}
                  onModalClose={() => setIsDateRangeModalOpen(false)}
                  onClick={() => setIsDateRangeModalOpen(true)}
                  filterName={filter}
                  popoverTitle="Filter decisions by evaluation date"
                  label={OPTIONAL_FILTER_FIELDS[filter].label}
                  fromDateTimeKey="evaluationDateAfter"
                  toDateTimeKey="evaluationDateBefore"
                />
              ) : (
                <Field
                  name={filter}
                  validate={OPTIONAL_FILTER_FIELDS[filter].validate}
                >
                  {({input}) => {
                    const field = OPTIONAL_FILTER_FIELDS[filter];

                    if (field.type === 'text') {
                      return (
                        <TextInputField
                          {...input}
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
                  }}
                </Field>
              )}
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
