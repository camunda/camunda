/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useInstancesCount} from 'modules/queries/processInstancesStatistics/useInstancesCount';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {Container, InlineNotification, Button} from '../styled';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';

type Props = {
  sourceFlowNodeId?: string;
  targetFlowNodeId?: string;
  onUndoClick?: () => void;
};

const BatchModificationNotification: React.FC<Props> = observer(
  ({sourceFlowNodeId, targetFlowNodeId, onUndoClick}) => {
    const selectedProcessInstanceIds =
      processInstancesSelectionStore.selectedProcessInstanceIds;
    const {data: instancesCount = 0} = useInstancesCount(
      {
        processInstanceKey: {
          $in: selectedProcessInstanceIds,
        },
      },
      sourceFlowNodeId,
    );

    const sourceFlowNodeName = sourceFlowNodeId
      ? processXmlStore.getFlowNodeName(sourceFlowNodeId)
      : undefined;
    const targetFlowNodeName = targetFlowNodeId
      ? processXmlStore.getFlowNodeName(targetFlowNodeId)
      : undefined;

    return (
      <Container>
        <InlineNotification
          hideCloseButton
          lowContrast
          kind="info"
          title=""
          subtitle={
            sourceFlowNodeName === undefined || targetFlowNodeName === undefined
              ? 'Please select where you want to move the selected instances on the diagram.'
              : `Modification scheduled: Move ${pluralSuffix(
                  instancesCount,
                  'instance',
                )} from “${sourceFlowNodeName}” to “${targetFlowNodeName}”. Press “Apply Modification” button to confirm.`
          }
        />
        {targetFlowNodeId && onUndoClick && (
          <Button kind="ghost" size="sm" onClick={onUndoClick}>
            Undo
          </Button>
        )}
      </Container>
    );
  },
);

export {BatchModificationNotification};
