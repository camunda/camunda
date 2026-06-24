/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useInstancesCount} from 'modules/queries/processInstancesStatistics/useInstancesCount';
import {pluralSuffix} from 'modules/utils/pluralSuffix';
import {Container, InlineNotification, Button} from './styled';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {getElementName} from 'modules/utils/elements';
import {getSelectedProcessInstancesFilter} from 'modules/queries/processInstancesStatistics/filters';

type Props = {
  sourceElementId?: string;
  targetElementId?: string;
  onUndoClick?: () => void;
};

const BatchModificationNotification: React.FC<Props> = observer(
  ({sourceElementId, targetElementId, onUndoClick}) => {
    const processDefinitionKey = useProcessDefinitionKeyContext();

    const {data: processDefinitionData} = useListViewXml({
      processDefinitionKey,
    });

    const processInstanceKeyFilter = getSelectedProcessInstancesFilter();
    const {data: instancesCount = 0} = useInstancesCount(
      {
        filter: {
          processInstanceKey: processInstanceKeyFilter,
        },
      },
      processDefinitionKey,
      sourceElementId,
    );

    const sourceElementName = getElementName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      elementId: sourceElementId,
    });

    const targetElementName = getElementName({
      businessObjects: processDefinitionData?.diagramModel.elementsById,
      elementId: targetElementId,
    });

    return (
      <Container>
        <InlineNotification
          hideCloseButton
          lowContrast
          kind="info"
          title=""
          subtitle={
            sourceElementName === '' || targetElementName === ''
              ? 'Please select where you want to move the selected instances on the diagram.'
              : `Modification scheduled: Move ${pluralSuffix(
                  instancesCount,
                  'instance',
                )} from “${sourceElementName}” to “${targetElementName}”. Press “Review Modification” button to confirm.`
          }
        />
        {targetElementId && onUndoClick && (
          <Button kind="ghost" size="sm" onClick={onUndoClick}>
            Undo
          </Button>
        )}
      </Container>
    );
  },
);

export {BatchModificationNotification};
