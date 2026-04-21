/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TableToolbar, TableBatchAction} from '@carbon/react';
import {TableBatchActions} from './styled';
import {TrashCan} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {decisionInstancesSelectionStore} from 'modules/stores/instancesSelection';

type Props = {
  selectedCount: number;
};

const Toolbar: React.FC<Props> = observer(({selectedCount}) => {
  if (selectedCount === 0) {
    return null;
  }

  return (
    <TableToolbar size="sm">
      <TableBatchActions
        shouldShowBatchActions={selectedCount > 0}
        totalSelected={selectedCount}
        onCancel={decisionInstancesSelectionStore.resetState}
        translateWithId={(id) => {
          switch (id) {
            case 'carbon.table.batch.cancel':
              return 'Discard';
            case 'carbon.table.batch.items.selected':
              return `${selectedCount} items selected`;
            case 'carbon.table.batch.item.selected':
              return `${selectedCount} item selected`;
            case 'carbon.table.batch.selectAll':
              return 'Select all items';
            default:
              return id;
          }
        }}
      >
        <TableBatchAction
          renderIcon={TrashCan}
          onClick={() => {}}
          data-testid="delete-decision-instances-batch-operation"
        >
          Delete
        </TableBatchAction>
      </TableBatchActions>
    </TableToolbar>
  );
});

export {Toolbar};
