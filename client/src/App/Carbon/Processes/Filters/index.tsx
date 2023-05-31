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
import {ProcessInstanceFilters} from 'modules/utils/filter';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {useFilters} from 'modules/hooks/useFilters';
import {ProcessField} from 'App/Processes/Filters/ProcessField';
import {ProcessVersionField} from 'App/Processes/Filters/ProcessVersionField';
import {FlowNodeField} from 'App/Processes/Filters/FlowNodeField';
import {
  Container,
  Title,
  Form as StyledForm,
} from 'modules/components/Carbon/FiltersPanel/styled';
import {FiltersPanel} from 'modules/components/Carbon/FiltersPanel';
import {
  CheckmarkOutline,
  RadioButtonChecked,
  WarningFilled,
} from 'modules/components/Carbon/StateIcon/styled';
import {CheckboxGroup} from './CheckboxGroup';
import {
  OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';

const initialValues: ProcessInstanceFilters = {
  active: true,
  incidents: true,
};

const Filters: React.FC = observer(() => {
  const filters = useFilters();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);

  return (
    <Form<ProcessInstanceFilters>
      onSubmit={(values) => {
        filters.setFiltersToURL(values);
      }}
      initialValues={filters.getFiltersFromUrl()}
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
              filters.setFiltersToURL(initialValues);
              setVisibleFilters([]);
            }}
          >
            <Container>
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
              <Stack gap={5}>
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
