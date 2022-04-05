/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import pluralSuffix from 'modules/utils/pluralSuffix';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesStore} from 'modules/stores/instances';
import * as Styled from './styled';
import CreateOperationDropdown from './CreateOperationDropdown';

const ListFooter: React.FC = observer(() => {
  const selectedCount = instanceSelectionStore.getSelectedInstanceCount();

  return (
    <Styled.Footer>
      {!instancesStore.areProcessInstancesEmpty && (
        <Styled.OperationButtonContainer>
          {selectedCount > 0 && (
            <CreateOperationDropdown
              label={`Apply Operation on ${pluralSuffix(
                selectedCount,
                'Instance'
              )}...`}
              selectedCount={selectedCount}
            />
          )}
        </Styled.OperationButtonContainer>
      )}
      <Styled.Copyright />
    </Styled.Footer>
  );
});

export {ListFooter};
