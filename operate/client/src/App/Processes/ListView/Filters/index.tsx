/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {Error} from '@carbon/react/icons';
import {Form} from 'react-final-form';
import isEqual from 'lodash/isEqual';
import {
  parseProcessInstancesFilter,
  updateProcessInstancesFilterSearchString,
  type ProcessInstancesFilter,
} from 'modules/utils/filter/processInstancesSearch';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {ProcessField} from './ProcessField';
import {ProcessVersionField} from './ProcessVersionField';
import {ElementField} from './ElementField';
import {
  Container,
  Title,
  Form as StyledForm,
} from 'modules/components/FiltersPanel/styled';
import {FiltersPanel} from 'modules/components/FiltersPanel';
import {
  CheckmarkOutline,
  RadioButtonChecked,
  WarningFilled,
} from 'modules/components/StateIcon/styled';
import {CheckboxGroup} from './CheckboxGroup';
import {
  type OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';
import {TenantField} from 'modules/components/TenantField';
import {batchModificationStore} from 'modules/stores/batchModification';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {
  getDefinitionIdentifier,
  splitDefinitionIdentifier,
} from 'modules/hooks/processDefinitions';
import {getClientConfig} from 'modules/utils/getClientConfig';

const initialValues: ProcessInstancesFilter = {
  active: true,
  incidents: true,
};

const Filters: React.FC = observer(() => {
  const clientConfig = getClientConfig();
  const isBatchModificationEnabled = batchModificationStore.state.isEnabled;
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const filterValues = parseProcessInstancesFilter(searchParams);
  if (filterValues.processDefinitionId && filterValues.tenantId !== 'all') {
    filterValues.processDefinitionId = getDefinitionIdentifier(
      filterValues.processDefinitionId,
      filterValues.tenantId,
    );
  }
  if (filterValues.tenantId === 'all') {
    delete filterValues.processDefinitionId;
    delete filterValues.processDefinitionVersion;
  }

  return (
    <Form<ProcessInstancesFilter>
      onSubmit={(values) => {
        navigate({
          search: updateProcessInstancesFilterSearchString(searchParams, {
            ...values,
            processDefinitionId: splitDefinitionIdentifier(
              values.processDefinitionId,
            ).definitionId,
          }),
        });
      }}
      initialValues={filterValues}
    >
      {({handleSubmit, form, values}) => (
        <StyledForm onSubmit={handleSubmit}>
          <FiltersPanel
            localStorageKey="isFiltersCollapsed"
            isResetButtonDisabled={
              (isEqual(initialValues, values) && visibleFilters.length === 0) ||
              isBatchModificationEnabled
            }
            onResetClick={() => {
              form.reset();
              navigate({
                search: updateProcessInstancesFilterSearchString(
                  searchParams,
                  initialValues,
                ),
              });
              setVisibleFilters([]);
              variableFilterStore.reset();
            }}
          >
            <Container>
              <AutoSubmit
                fieldsToSkipTimeout={[
                  'tenantId',
                  'processDefinitionId',
                  'processDefinitionVersion',
                  'elementId',
                  'active',
                  'incidents',
                  'completed',
                  'canceled',
                  'hasRetriesLeft',
                ]}
              />
              <Stack gap={5}>
                {clientConfig.multiTenancyEnabled && (
                  <div>
                    <Title>Tenant</Title>
                    <TenantField
                      onChange={() => {
                        form.change('processDefinitionId', undefined);
                        form.change('processDefinitionVersion', undefined);
                      }}
                    />
                  </div>
                )}
                <div>
                  <Title>Process</Title>
                  <Stack gap={5}>
                    <ProcessField />
                    <ProcessVersionField />
                    <ElementField />
                  </Stack>
                </div>
                <div>
                  <Title>Instances States</Title>
                  <Stack gap={3}>
                    <CheckboxGroup
                      groupLabel="Running Instances"
                      dataTestId="filter-running-instances"
                      items={[
                        {
                          label: 'Active',
                          name: 'active',
                          Icon: RadioButtonChecked,
                        },
                        {
                          label: 'Incidents',
                          name: 'incidents',
                          Icon: WarningFilled,
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
                          Icon: CheckmarkOutline,
                        },
                        {
                          label: 'Canceled',
                          name: 'canceled',
                          Icon: Error,
                        },
                      ]}
                    />
                  </Stack>
                </div>
                <OptionalFiltersFormGroup
                  visibleFilters={visibleFilters}
                  onVisibleFilterChange={setVisibleFilters}
                />
              </Stack>
            </Container>
          </FiltersPanel>
        </StyledForm>
      )}
    </Form>
  );
});

export {Filters};
