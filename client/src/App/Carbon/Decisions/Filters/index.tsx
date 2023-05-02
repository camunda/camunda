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
import {Container} from './styled';

import {observer} from 'mobx-react';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {DecisionsFormGroup} from './DecisionsFormGroup';
import {CollapsablePanel} from './CollapsablePanel';
import {InstancesStatesFormGroup} from './InstancesStatesFormGroup';
import {Stack} from '@carbon/react';

type LocationType = Omit<Location, 'state'> & {
  state: {hideOptionalFilters?: boolean};
};

const Filters: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();

  return (
    <CollapsablePanel label="Filters">
      <Container>
        <Form<DecisionInstanceFilters>
          onSubmit={(values) => {
            navigate({
              search: updateDecisionsFiltersSearchString(
                location.search,
                values
              ),
            });
          }}
          initialValues={getDecisionInstanceFilters(location.search)}
        >
          {({handleSubmit}) => (
            <form onSubmit={handleSubmit}>
              <AutoSubmit
                fieldsToSkipTimeout={['name', 'version', 'evaluated', 'failed']}
              />
              <Stack gap={5}>
                <DecisionsFormGroup />
                <InstancesStatesFormGroup />
              </Stack>
            </form>
          )}
        </Form>
      </Container>
    </CollapsablePanel>
  );
});

export {Filters};
