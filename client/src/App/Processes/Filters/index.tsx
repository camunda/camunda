/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Field, Form} from 'react-final-form';
import {useLocation, Location} from 'react-router-dom';
import {isEqual, intersection} from 'lodash';
import {
  FiltersForm,
  ResetButtonContainer,
  Fields,
  StatesHeader,
  InstanceStates,
  ProcessHeader,
  OptionalFilters,
  MoreFiltersDropdown,
  FormGroup,
  DeleteIcon,
  TextField,
} from './styled';
import {ProcessField} from './ProcessField';
import {ProcessVersionField} from './ProcessVersionField';
import {FlowNodeField} from './FlowNodeField';
import {CheckboxGroup} from './CheckboxGroup';
import {Button} from 'modules/components/Button';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {
  getProcessInstanceFilters,
  ProcessInstanceFilters,
  ProcessInstanceFilterField,
} from 'modules/utils/filter';
import {FiltersPanel} from './FiltersPanel';
import {observer} from 'mobx-react';
import {useFilters} from 'modules/hooks/useFilters';
import {
  validateIdsCharacters,
  validateIdsLength,
  validatesIdsComplete,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validateOperationIdCharacters,
  validateOperationIdComplete,
} from 'modules/validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {FieldValidator} from 'final-form';
import {Variable} from './VariableField';
import {DateRangeField} from 'modules/components/DateRangeField';
import {tracking} from 'modules/tracking';
import {dateRangePopoverStore} from 'modules/stores/dateRangePopover';

type OptionalFilter =
  | 'variable'
  | 'ids'
  | 'parentInstanceId'
  | 'operationId'
  | 'errorMessage'
  | 'startDateRange'
  | 'endDateRange';

const optionalFilters: Array<OptionalFilter> = [
  'variable',
  'ids',
  'operationId',
  'parentInstanceId',
  'errorMessage',
  'startDateRange',
  'endDateRange',
];

type LocationType = Omit<Location, 'state'> & {
  state: {hideOptionalFilters?: boolean; refreshContent?: boolean};
};

const initialValues: ProcessInstanceFilters = {
  active: true,
  incidents: true,
};

const OPTIONAL_FILTER_FIELDS: Record<
  OptionalFilter,
  {
    label: string;
    placeholder?: string;
    type?: 'multiline' | 'text';
    rows?: number;
    validate?: FieldValidator<string | undefined>;
    keys: ProcessInstanceFilterField[];
  }
> = {
  variable: {
    keys: ['variableName', 'variableValue'],
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
      validatesIdsComplete
    ),
  },
  operationId: {
    keys: ['operationId'],
    label: 'Operation Id',
    type: 'text',
    validate: mergeValidators(
      validateOperationIdCharacters,
      validateOperationIdComplete
    ),
  },
  parentInstanceId: {
    keys: ['parentInstanceId'],
    label: 'Parent Process Instance Key',
    type: 'text',
    validate: mergeValidators(
      validateParentInstanceIdComplete,
      validateParentInstanceIdNotTooLong,
      validateParentInstanceIdCharacters
    ),
  },
  errorMessage: {
    keys: ['errorMessage'],
    label: 'Error Message',
    type: 'text',
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

const Filters: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const filters = useFilters();

  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const unselectedOptionalFilters = optionalFilters.filter(
    (filter) => !visibleFilters.includes(filter)
  );

  useEffect(() => {
    const filters = getProcessInstanceFilters(location.search);

    setVisibleFilters((currentVisibleFilters) => {
      return Array.from(
        new Set([
          ...(location.state?.hideOptionalFilters ? [] : currentVisibleFilters),
          ...([
            ...intersection(Object.keys(filters), optionalFilters),
            ...('variableName' in filters && 'variableValue' in filters
              ? ['variable']
              : []),
            ...('startDateAfter' in filters && 'startDateBefore' in filters
              ? ['startDateRange']
              : []),
            ...('endDateAfter' in filters && 'endDateBefore' in filters
              ? ['endDateRange']
              : []),
          ] as OptionalFilter[]),
        ])
      );
    });
  }, [location.state, location.search]);

  return (
    <FiltersPanel>
      <Form<ProcessInstanceFilters>
        onSubmit={(values) => {
          filters.setFiltersToURL(values);
        }}
        initialValues={filters.getFiltersFromUrl()}
      >
        {({handleSubmit, form, values}) => (
          <FiltersForm onSubmit={handleSubmit}>
            <Fields>
              <AutoSubmit
                fieldsToSkipTimeout={[
                  'process',
                  'version',
                  'flowNodeId',
                  'active',
                  'incidents',
                  'completed',
                  'canceled',
                ]}
              />
              <ProcessHeader appearance="emphasis">Process</ProcessHeader>
              <ProcessField />
              <ProcessVersionField />
              <FlowNodeField />
              <InstanceStates>
                <StatesHeader appearance="emphasis">
                  Instance States
                </StatesHeader>
                <CheckboxGroup
                  groupLabel="Running Instances"
                  dataTestId="filter-running-instances"
                  items={[
                    {
                      label: 'Active',
                      name: 'active',
                      icon: {icon: 'state:ok', color: 'success'},
                    },
                    {
                      label: 'Incidents',
                      name: 'incidents',
                      icon: {icon: 'state:incident', color: 'danger'},
                    },
                  ]}
                />
                <CheckboxGroup
                  groupLabel="Finished Instances"
                  dataTestId="filter-finished-instances"
                  items={[
                    {
                      label: 'Completed',
                      name: 'completed',
                      icon: {icon: 'state:completed', color: 'medLight'},
                    },
                    {
                      label: 'Canceled',
                      name: 'canceled',
                      icon: {icon: 'stop', color: 'medDark'},
                    },
                  ]}
                />
              </InstanceStates>

              {unselectedOptionalFilters.length > 0 && (
                <MoreFiltersDropdown
                  trigger={{type: 'label', label: 'More Filters'}}
                  data-testid="more-filters-dropdown"
                  options={[
                    {
                      options: unselectedOptionalFilters.map((filter) => ({
                        label: OPTIONAL_FILTER_FIELDS[filter].label,
                        handler: () => {
                          setVisibleFilters(
                            Array.from(
                              new Set([...visibleFilters, ...[filter]])
                            )
                          );
                          tracking.track({
                            eventName: 'optional-filter-selected',
                            filterName: filter,
                          });
                          if (
                            ['startDateRange', 'endDateRange'].includes(filter)
                          ) {
                            setTimeout(() =>
                              dateRangePopoverStore.setVisiblePopover(filter)
                            );
                          }
                        },
                      })),
                    },
                  ]}
                />
              )}

              <OptionalFilters>
                {visibleFilters.map((filter) => (
                  <FormGroup
                    key={filter}
                    order={visibleFilters.indexOf(filter)}
                  >
                    <DeleteIcon
                      icon="delete"
                      data-testid={`delete-${filter}`}
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
                    />
                    {(() => {
                      switch (filter) {
                        case 'variable':
                          return <Variable />;
                        case 'startDateRange':
                          return (
                            <DateRangeField
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
                            >
                              {({input}) => (
                                <TextField
                                  {...input}
                                  data-testid={`optional-filter-${filter}`}
                                  label={OPTIONAL_FILTER_FIELDS[filter].label}
                                  type={OPTIONAL_FILTER_FIELDS[filter].type}
                                  rows={OPTIONAL_FILTER_FIELDS[filter].rows}
                                  placeholder={
                                    OPTIONAL_FILTER_FIELDS[filter].placeholder
                                  }
                                  shouldDebounceError={false}
                                  autoFocus
                                />
                              )}
                            </Field>
                          );
                      }
                    })()}
                  </FormGroup>
                ))}
              </OptionalFilters>
            </Fields>

            <ResetButtonContainer>
              <Button
                title="Reset Filters"
                size="small"
                disabled={
                  isEqual(initialValues, values) && visibleFilters.length === 0
                }
                type="reset"
                onClick={() => {
                  form.reset();
                  filters.setFiltersToURL(initialValues);
                  setVisibleFilters([]);
                }}
              >
                Reset Filters
              </Button>
            </ResetButtonContainer>
          </FiltersForm>
        )}
      </Form>
    </FiltersPanel>
  );
});

export {Filters};
