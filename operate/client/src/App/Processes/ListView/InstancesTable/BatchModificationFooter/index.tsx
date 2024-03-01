/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';
import {observer} from 'mobx-react';
import {batchModificationStore} from 'modules/stores/batchModification';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {Stack} from './styled';

const BatchModificationFooter: React.FC = observer(() => {
  return (
    <Stack orientation="horizontal" gap={5}>
      <Button
        kind="secondary"
        size="sm"
        onClick={batchModificationStore.disable}
      >
        Exit
      </Button>
      <Button
        size="sm"
        disabled={
          processInstancesSelectionStore.selectedProcessInstanceCount < 1
        }
      >
        Apply Modification
      </Button>
    </Stack>
  );
});

export {BatchModificationFooter};
