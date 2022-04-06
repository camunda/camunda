/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {Button} from 'modules/components/Button';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {
  getProcessInstanceFilters,
  ProcessInstanceFilters,
} from 'modules/utils/filter';
import {storeStateLocally} from 'modules/utils/localStorage';
import {FiltersPanel} from './FiltersPanel';
import {observer} from 'mobx-react';
import {
  OptionalFilter,
  processInstancesVisibleFiltersStore,
} from 'modules/stores/processInstancesVisibleFilters';
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

  const {visibleFilters} = processInstancesVisibleFiltersStore.state;

  const initialValues: ProcessInstanceFilters = {
    active: true,
    incidents: true,
  };

  useEffect(() => {
    const filters = getProcessInstanceFilters(location.search);
    storeStateLocally({
      filters,
    });

    processInstancesVisibleFiltersStore.addVisibleFilters([
      ...intersection(
        Object.keys(filters),
        processInstancesVisibleFiltersStore.possibleOptionalFilters
      ),
      ...('variableName' in filters && 'variableValue' in filters
        ? ['variable']
        : []),
    ] as OptionalFilter[]);
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

              {!processInstancesVisibleFiltersStore.areAllFiltersVisible && (
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
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['variable']
                                  );
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('ids')
                          ? [
                              {
                                label: 'Instance Id(s)',
                                handler: () => {
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['ids']
                                  );
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('operationId')
                          ? [
                              {
                                label: 'Operation Id',
                                handler: () => {
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['operationId']
                                  );
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('parentInstanceId')
                          ? [
                              {
                                label: 'Parent Instance Id',
                                handler: () => {
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['parentInstanceId']
                                  );
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('errorMessage')
                          ? [
                              {
                                label: 'Error Message',
                                handler: () => {
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['errorMessage']
                                  );
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('startDate')
                          ? [
                              {
                                label: 'Start Date',
                                handler: () => {
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['startDate']
                                  );
                                },
                              },
                            ]
                          : []),
                        ...(!visibleFilters.includes('endDate')
                          ? [
                              {
                                label: 'End Date',
                                handler: () => {
                                  processInstancesVisibleFiltersStore.addVisibleFilters(
                                    ['endDate']
                                  );
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
                  processInstancesVisibleFiltersStore.reset();
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
