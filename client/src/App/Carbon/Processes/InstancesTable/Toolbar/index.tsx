/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TableToolbar} from '@carbon/react';
import {TableBatchActions} from './styled';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';

type Props = {
  selectedInstancesCount: number;
};

const Toolbar: React.FC<Props> = ({selectedInstancesCount}) => {
  if (selectedInstancesCount === 0) {
    return null;
  }

  return (
    <TableToolbar size="sm">
      <TableBatchActions
        shouldShowBatchActions={selectedInstancesCount > 0}
        totalSelected={selectedInstancesCount}
        onCancel={processInstancesSelectionStore.reset}
      ></TableBatchActions>
    </TableToolbar>
  );
};

export {Toolbar};
