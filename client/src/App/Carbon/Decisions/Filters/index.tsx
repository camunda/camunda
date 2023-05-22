/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  DecisionInstanceFilters,
  getDecisionInstanceFilters,
  updateDecisionsFiltersSearchString,
} from 'modules/utils/filter';
import {Form} from 'react-final-form';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {Container, Footer} from './styled';

import {observer} from 'mobx-react';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {DecisionsFormGroup} from './DecisionsFormGroup';
import {CollapsablePanel} from './CollapsablePanel';
import {InstancesStatesFormGroup} from './InstancesStatesFormGroup';
import {Button, Stack} from '@carbon/react';
import {
  OptionalFilter,
  OptionalFiltersFormGroup,
} from './OptionalFiltersFormGroup';
import {isEqual} from 'lodash';
import {useState} from 'react';
import {CarbonLocations} from 'modules/carbonRoutes';

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
      initialValues={getDecisionInstanceFilters(location.search)}
    >
      {({handleSubmit, form, values}) => (
        <form onSubmit={handleSubmit} style={{height: '100%'}}>
          <CollapsablePanel
            label="Filters"
            footer={
              <Footer>
                <Button
                  kind="ghost"
                  size="sm"
                  disabled={
                    isEqual(initialValues, values) &&
                    visibleFilters.length === 0
                  }
                  type="reset"
                  onClick={() => {
                    form.reset();
                    navigate(CarbonLocations.decisions(initialValues));
                    setVisibleFilters([]);
                  }}
                >
                  Reset filters
                </Button>
              </Footer>
            }
          >
            <Container>
              <AutoSubmit
                fieldsToSkipTimeout={['name', 'version', 'evaluated', 'failed']}
              />
              <Stack gap={8}>
                <Stack gap={5}>
                  <DecisionsFormGroup />
                  <InstancesStatesFormGroup />
                </Stack>
                <OptionalFiltersFormGroup
                  visibleFilters={visibleFilters}
                  onVisibleFilterChange={setVisibleFilters}
                />
              </Stack>
            </Container>
          </CollapsablePanel>
        </form>
      )}
    </Form>
  );
});

export {Filters};
