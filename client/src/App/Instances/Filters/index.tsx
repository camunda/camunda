/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Form} from 'react-final-form';
import {useLocation} from 'react-router-dom';
import {isEqual, intersection} from 'lodash';
import {
  FiltersForm,
  ResetButtonContainer,
  Fields,
  StatesHeader,
  InstanceStates,
  ProcessHeader,
  OrderedFilters,
  MoreFiltersDropdown,
} from './styled';
import {ProcessField} from './ProcessField';
import {ProcessVersionField} from './ProcessVersionField';
import {FlowNodeField} from './FlowNodeField';
import {CheckboxGroup} from './CheckboxGroup';
import Button from 'modules/components/Button';
import {AutoSubmit} from './AutoSubmit';
import {
  getProcessInstanceFilters,
  ProcessInstanceFilters,
} from 'modules/utils/filter';
import {storeStateLocally} from 'modules/utils/localStorage';
import {FiltersPanel} from './FiltersPanel';
import {observer} from 'mobx-react';
import {
  visibleFiltersStore,
  optionalFilters,
  OptionalFilter,
} from 'modules/stores/visibleFilters';
import {Ids} from './OptionalFilters/Ids';
import {ParentInstanceIds} from './OptionalFilters/ParentInstanceIds';
import {ErrorMessage} from './OptionalFilters/ErrorMessage';
import {StartDate} from './OptionalFilters/StartDate';
import {EndDate} from './OptionalFilters/EndDate';
import {Variable} from './OptionalFilters/Variable';
import {OperationId} from './OptionalFilters/OperationId';
import {useFilters} from 'modules/hooks/useFilters';

const Filters: React.FC = observer(() => {
  const location = useLocation();
  const filters = useFilters();

  const {visibleFilters} = visibleFiltersStore.state;

  const initialValues: ProcessInstanceFilters = {
    active: true,
    incidents: true,
  };

  useEffect(() => {
    const filters = getProcessInstanceFilters(location.search);
    storeStateLocally({
      filters,
    });

    visibleFiltersStore.addVisibleFilters([
      ...intersection(Object.keys(filters), optionalFilters),
      ...('variableName' in filters && 'variableValue' in filters
        ? ['variable']
        : []),
    ] as Array<OptionalFilter>);
  }, [location.search]);

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

              {!visibleFiltersStore.areAllFiltersVisible && (
                <MoreFiltersDropdown
                  trigger={{type: 'label', label: 'More Filters'}}
                  data-testid="more-filters-dropdown"
                  options={[
                    {
                      options: [
                        ...(!visibleFilters.includes('variable')
                          ? [
                              {
                                label: 'Variable',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'variable',
                                  ]);
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('ids')
                          ? [
                              {
                                label: 'Instance Id(s)',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'ids',
                                  ]);
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('operationId')
                          ? [
                              {
                                label: 'Operation Id',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'operationId',
                                  ]);
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('parentInstanceId')
                          ? [
                              {
                                label: 'Parent Instance Id',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'parentInstanceId',
                                  ]);
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('errorMessage')
                          ? [
                              {
                                label: 'Error Message',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'errorMessage',
                                  ]);
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('startDate')
                          ? [
                              {
                                label: 'Start Date',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'startDate',
                                  ]);
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('endDate')
                          ? [
                              {
                                label: 'End Date',
                                handler: () => {
                                  visibleFiltersStore.addVisibleFilters([
                                    'endDate',
                                  ]);
                                },
                              },
                            ]
                          : []),
                      ],
                    },
                  ]}
                />
              )}
              <OrderedFilters>
                {visibleFilters.includes('ids') && <Ids />}
                {visibleFilters.includes('parentInstanceId') && (
                  <ParentInstanceIds />
                )}
                {visibleFilters.includes('errorMessage') && <ErrorMessage />}
                {visibleFilters.includes('startDate') && <StartDate />}
                {visibleFilters.includes('endDate') && <EndDate />}
                {visibleFilters.includes('variable') && <Variable />}
                {visibleFilters.includes('operationId') && <OperationId />}
              </OrderedFilters>
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
                  visibleFiltersStore.reset();
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
