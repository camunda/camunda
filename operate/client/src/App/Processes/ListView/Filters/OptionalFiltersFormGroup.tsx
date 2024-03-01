/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {observer} from 'mobx-react';
import {useEffect, useState} from 'react';
import {useLocation, Location} from 'react-router-dom';
import {FieldValidator} from 'final-form';
import {Close} from '@carbon/react/icons';
import intersection from 'lodash/intersection';
import {ProcessInstanceFilterField} from 'modules/utils/filter/shared';
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
                  align="top-right"
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
