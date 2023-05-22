/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {OptionalFiltersMenu} from 'modules/components/Carbon/OptionalFilters';
import {DateRangeField} from 'modules/components/Carbon/DateRangeField';
import {Field, useForm} from 'react-final-form';
import {IconButton, Stack} from '@carbon/react';
import {TextInputField} from 'modules/components/Carbon/TextInputField';
import {TextAreaField} from 'modules/components/Carbon/TextAreaField';
import {ButtonContainer, FieldContainer} from './styled';
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
    placeholder: 'separated by space or comma',
    rows: 1,
    validate: mergeValidators(
      validateDecisionIdsCharacters,
      validateDecisionIdsLength,
      validatesDecisionIdsComplete
    ),
  },
  processInstanceId: {
    keys: ['processInstanceId'],
    label: 'Process Instance Key',
    type: 'text',
    validate: mergeValidators(
      validateParentInstanceIdComplete,
      validateParentInstanceIdNotTooLong,
      validateParentInstanceIdCharacters
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

const OptionalFiltersFormGroup: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const form = useForm();

  useEffect(() => {
    if (location.state?.hideOptionalFilters) {
      setVisibleFilters([]);
    }
  }, [location.state]);

  useEffect(() => {
    const params = Array.from(
      new URLSearchParams(location.search).keys()
    ).filter((param) =>
      (optionalFilters as string[]).includes(param)
    ) as OptionalFilter[];

    const filters = getDecisionInstanceFilters(location.search);

    setVisibleFilters((currentVisibleFilters) => {
      return Array.from(
        new Set([
          ...currentVisibleFilters,
          ...params,
          ...('evaluationDateAfter' in filters &&
          'evaluationDateBefore' in filters
            ? ['evaluationDateRange']
            : []),
        ] as OptionalFilter[])
      );
    });
  }, [location.search]);

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
          setVisibleFilters(
            Array.from(new Set([...visibleFilters, ...[filter]]))
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
                  setVisibleFilters(
                    visibleFilters.filter(
                      (visibleFilter) => visibleFilter !== filter
                    )
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
});

export {OptionalFiltersFormGroup};
