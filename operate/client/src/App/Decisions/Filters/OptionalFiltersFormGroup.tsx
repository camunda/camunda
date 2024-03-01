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
import {
  DecisionInstanceFilterField,
  getDecisionInstanceFilters,
} from 'modules/utils/filter';
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
    keys: DecisionInstanceFilterField[];
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
    const form = useForm();

    useEffect(() => {
      if (location.state?.hideOptionalFilters) {
        onVisibleFilterChange([]);
      }
    }, [location.state, onVisibleFilterChange]);

    useEffect(() => {
      const params = Array.from(
        new URLSearchParams(location.search).keys(),
      ).filter((param) =>
        (optionalFilters as string[]).includes(param),
      ) as OptionalFilter[];

      const filters = getDecisionInstanceFilters(location.search);

      onVisibleFilterChange((currentVisibleFilters) =>
        Array.from(
          new Set([
            ...currentVisibleFilters,
            ...params,
            ...('evaluationDateAfter' in filters &&
            'evaluationDateBefore' in filters
              ? ['evaluationDateRange']
              : []),
          ] as OptionalFilter[]),
        ),
      );
    }, [location.search, onVisibleFilterChange]);

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
