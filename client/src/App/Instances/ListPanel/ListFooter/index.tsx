/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import pluralSuffix from 'modules/utils/pluralSuffix';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {instancesStore} from 'modules/stores/instances';
import * as Styled from './styled';
import CreateOperationDropdown from './CreateOperationDropdown';

type Props = {
  isCollapsed: boolean;
};

const ListFooter: React.FC<Props> = observer(({isCollapsed}) => {
  const selectedCount = instanceSelectionStore.getSelectedInstanceCount();

  return (
    <Styled.Footer>
      {!isCollapsed && !instancesStore.areProcessInstancesEmpty && (
        <Styled.OperationButtonContainer>
          {/* @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'. */}
          {selectedCount > 0 && (
            <CreateOperationDropdown
              label={`Apply Operation on ${pluralSuffix(
                selectedCount,
                'Instance'
              )}...`}
              // @ts-expect-error ts-migrate(2322) FIXME: Type 'null' is not assignable to type 'number'.
              selectedCount={selectedCount}
            />
          )}
        </Styled.OperationButtonContainer>
      )}
      <Styled.Copyright />
    </Styled.Footer>
  );
});

export default ListFooter;
