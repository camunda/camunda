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
import {dateRangePopoverStore} from 'modules/stores/dateRangePopover';
import {OptionalFiltersMenu} from 'modules/components/Carbon/OptionalFilters';

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

  return (
    <div>
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
              dateRangePopoverStore.setVisiblePopover(filter);
            });
          }
        }}
      />
      <div>
        {visibleFilters.map((filter) => (
          <div key={filter}>{filter}</div>
        ))}
      </div>
    </div>
  );
});

export {OptionalFiltersFormGroup};
