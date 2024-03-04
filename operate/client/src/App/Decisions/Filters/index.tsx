/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  DecisionInstanceFilters,
  getDecisionInstanceFilters,
  updateDecisionsFiltersSearchString,
} from 'modules/utils/filter';
import {Form} from 'react-final-form';
import {useLocation, useNavigate, Location} from 'react-router-dom';
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
  OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';
import {isEqual} from 'lodash';
import {useState} from 'react';
import {Locations} from 'modules/Routes';
import {FiltersPanel} from 'modules/components/FiltersPanel';
import {TenantField} from 'modules/components/TenantField';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

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
  const filtersFromUrl = getDecisionInstanceFilters(location.search);
  return (
    <Form<DecisionInstanceFilters>
      onSubmit={(values) => {
        navigate({
          search: updateDecisionsFiltersSearchString(location.search, {
            ...values,
            ...(values.name !== undefined
              ? {
                  name: groupedDecisionsStore.state.decisions.find(
                    ({key}) => key === values.name,
                  )?.decisionId,
                }
              : {}),
          }),
        });
      }}
      initialValues={{
        ...filtersFromUrl,
        ...(filtersFromUrl.name !== undefined
          ? {
              name: groupedDecisionsStore.getDecision(
                filtersFromUrl.name,
                filtersFromUrl.tenant,
              )?.key,
            }
          : {}),
      }}
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
                        onChange={(selectedItem) => {
                          form.change('name', undefined);
                          form.change('version', undefined);

                          groupedDecisionsStore.fetchDecisions(selectedItem);
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
