/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {Form} from 'react-final-form';
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
                fieldsToSkipTimeout={['name', 'version', 'evaluated', 'failed']}
              />

              <Title>Processes</Title>
              <Stack gap={5}>
                <ProcessField />
                <ProcessVersionField />
                <FlowNodeField />
              </Stack>
            </Container>
          </FiltersPanel>
        </StyledForm>
      )}
    </Form>
  );
});

export {Filters};
