/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {Error} from '@carbon/react/icons';
import {Form} from 'react-final-form';
import isEqual from 'lodash/isEqual';
import {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {useFilters} from 'modules/hooks/useFilters';
import {ProcessField} from './ProcessField';
import {ProcessVersionField} from './ProcessVersionField';
import {FlowNodeField} from './FlowNodeField';
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
  OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';
import {TenantField} from 'modules/components/TenantField';
import {processesStore} from 'modules/stores/processes/processes.list';

const initialValues: ProcessInstanceFilters = {
  active: true,
  incidents: true,
};

const Filters: React.FC = observer(() => {
  const filters = useFilters();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const filtersFromUrl = filters.getFilters();

  return (
    <Form<ProcessInstanceFilters>
      onSubmit={(values) => {
        filters.setFilters({
          ...values,
          ...(values.process !== undefined
            ? {
                process: processesStore.state.processes.find(
                  ({key}) => key === values.process,
                )?.bpmnProcessId,
              }
            : {}),
        });
      }}
      initialValues={{
        ...filtersFromUrl,
        ...(filtersFromUrl.process !== undefined
          ? {
              process: processesStore.getProcess({
                bpmnProcessId: filtersFromUrl.process,
                tenantId: filtersFromUrl.tenant,
              })?.key,
            }
          : {}),
      }}
    >
      {({handleSubmit, form, values}) => (
        <StyledForm onSubmit={handleSubmit}>
          <FiltersPanel
            localStorageKey="isFiltersCollapsed"
            isResetButtonDisabled={
              isEqual(initialValues, values) && visibleFilters.length === 0
            }
            onResetClick={() => {
              form.reset();
              filters.setFilters(initialValues);
              setVisibleFilters([]);
            }}
          >
            <Container>
              <AutoSubmit
                fieldsToSkipTimeout={[
                  'tenant',
                  'process',
                  'version',
                  'flowNodeId',
                  'active',
                  'incidents',
                  'completed',
                  'canceled',
                  'retriesLeft',
                ]}
              />
              <Stack gap={5}>
                {window.clientConfig?.multiTenancyEnabled && (
                  <div>
                    <Title>Tenant</Title>
                    <TenantField
                      onChange={(selectedItem) => {
                        form.change('process', undefined);
                        form.change('version', undefined);

                        processesStore.fetchProcesses(selectedItem);
                      }}
                    />
                  </div>
                )}
                <div>
                  <Title>Process</Title>
                  <Stack gap={5}>
                    <ProcessField />
                    <ProcessVersionField />
                    <FlowNodeField />
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
