/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseDecisionInstancesFilter,
  updateDecisionsFiltersSearchString,
  type DecisionInstanceFilters,
} from 'modules/utils/filter/decisionInstancesSearch';
import {Form} from 'react-final-form';
import {useLocation, useNavigate, type Location} from 'react-router-dom';
import {
  Container,
  Form as StyledForm,
  Title,
} from 'modules/components/FiltersPanel/styled';
import {observer} from 'mobx-react';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {DecisionsFormGroup} from './DecisionsFormGroup';
import {InstancesStatesFormGroup} from './InstancesStatesFormGroup';
import {Stack} from '@carbon/react';
import {
  type OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';
import isEqual from 'lodash/isEqual';
import {useState} from 'react';
import {Locations} from 'modules/Routes';
import {FiltersPanel} from 'modules/components/FiltersPanel';
import {TenantField} from 'modules/components/TenantField';

const initialValues: DecisionInstanceFilters = {
  evaluated: true,
  failed: true,
};

type LocationType = Omit<Location, 'state'> & {
  state: {hideOptionalFilters?: boolean};
};

const Filters: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);

  return (
    <Form<DecisionInstanceFilters>
      onSubmit={(values) => {
        navigate({
          search: updateDecisionsFiltersSearchString(location.search, values),
        });
      }}
      initialValues={parseDecisionInstancesFilter(location.search)}
    >
      {({handleSubmit, form, values}) => (
        <StyledForm onSubmit={handleSubmit}>
          <FiltersPanel
            localStorageKey="isDecisionsFiltersCollapsed"
            onResetClick={() => {
              form.reset();
              navigate(Locations.decisions(initialValues));
              setVisibleFilters([]);
            }}
            isResetButtonDisabled={
              isEqual(initialValues, values) && visibleFilters.length === 0
            }
          >
            <Container>
              <AutoSubmit
                fieldsToSkipTimeout={[
                  'tenant',
                  'name',
                  'version',
                  'evaluated',
                  'failed',
                ]}
              />
              <Stack gap={8}>
                <Stack gap={5}>
                  {window.clientConfig?.multiTenancyEnabled && (
                    <div>
                      <Title>Tenant</Title>
                      <TenantField
                        onChange={() => {
                          form.change('name', undefined);
                          form.change('version', undefined);
                        }}
                      />
                    </div>
                  )}
                  <DecisionsFormGroup />
                  <InstancesStatesFormGroup />
                </Stack>
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
