/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, PlusIcon, StopIcon, WarningIcon} from './styled';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

type Props = {
  flowNodeInstance: Pick<
    FlowNodeInstance,
    'flowNodeId' | 'isPlaceholder' | 'endDate' | 'treePath'
  >;
};

const ModificationIcons: React.FC<Props> = observer(({flowNodeInstance}) => {
  const instanceKeyHierarchy = flowNodeInstance.treePath.split('/');

  const hasCancelModification =
    modificationsStore.modificationsByFlowNode[flowNodeInstance.flowNodeId]
      ?.areAllTokensCanceled ||
    instanceKeyHierarchy.some((instanceKey) =>
      modificationsStore.hasPendingCancelOrMoveModification(
        flowNodeInstance.flowNodeId,
        instanceKey
      )
    );

  return (
    <Container>
      <>
        {flowNodeInstance.isPlaceholder && (
          <>
            <WarningIcon title="Ensure to add/edit variables if required, input/output mappings are not executed during modification" />
            <PlusIcon title="This flow node instance is planned to be added" />
          </>
        )}
        {hasCancelModification &&
          !flowNodeInstance.isPlaceholder &&
          flowNodeInstance.endDate === null && (
            <StopIcon title="This flow node instance is planned to be canceled" />
          )}
      </>
    </Container>
  );
});

export {ModificationIcons};
