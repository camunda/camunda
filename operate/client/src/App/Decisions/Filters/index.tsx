/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseDecisionsFilter,
  updateDecisionsFilterSearchString,
  type DecisionsFilter,
} from 'modules/utils/filter/decisionsFilter';
import {Form} from 'react-final-form';
import {useNavigate, useSearchParams} from 'react-router-dom';
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
import {
  getDefinitionIdentifier,
  getDefinitionIdFromIdentifier,
} from 'modules/hooks/decisionDefinitions';
import {getClientConfig} from 'modules/utils/getClientConfig';

const initialValues: DecisionsFilter = {
  evaluated: true,
  failed: true,
};

const Filters: React.FC = observer(() => {
  const clientConfig = getClientConfig();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
  const filterValues = parseDecisionsFilter(params);
  if (filterValues.decisionDefinitionId && filterValues.tenantId !== 'all') {
    filterValues.decisionDefinitionId = getDefinitionIdentifier(
      filterValues.decisionDefinitionId,
      filterValues.tenantId,
    );
  }
  if (filterValues.tenantId === 'all') {
    delete filterValues.decisionDefinitionId;
    delete filterValues.decisionDefinitionVersion;
  }

  return (
    <Form<DecisionsFilter>
      onSubmit={(values) => {
        navigate({
          search: updateDecisionsFilterSearchString(params, {
            ...values,
            decisionDefinitionId: getDefinitionIdFromIdentifier(
              values.decisionDefinitionId,
            ),
          }),
        });
      }}
      initialValues={filterValues}
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
                  'tenantId',
                  'decisionDefinitionId',
                  'decisionDefinitionVersion',
                  'evaluated',
                  'failed',
                ]}
              />
              <Stack gap={8}>
                <Stack gap={5}>
                  {clientConfig.multiTenancyEnabled && (
                    <div>
                      <Title>Tenant</Title>
                      <TenantField
                        onChange={() => {
                          form.change('decisionDefinitionId', undefined);
                          form.change('decisionDefinitionVersion', undefined);
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
