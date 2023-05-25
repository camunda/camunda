/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {Form} from 'react-final-form';
import {Error} from '@carbon/react/icons';
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

const Filters: React.FC = observer(() => {
  const filters = useFilters();

  return (
    <Form<ProcessInstanceFilters>
      onSubmit={(values) => {
        filters.setFiltersToURL(values);
      }}
      initialValues={filters.getFiltersFromUrl()}
    >
      {({handleSubmit}) => (
        <StyledForm onSubmit={handleSubmit}>
          <FiltersPanel
            localStorageKey="isFiltersCollapsed"
            isResetButtonDisabled={true}
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
              </Stack>
            </Container>
          </FiltersPanel>
        </StyledForm>
      )}
    </Form>
  );
});

export {Filters};
