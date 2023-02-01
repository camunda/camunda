/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {EmptyPanel} from 'modules/components/EmptyPanel';
import {observer} from 'mobx-react';
import {DataTable} from 'modules/components/DataTable';
import {IOMappingInfoBanner} from './IOMappingInfoBanner';
import {getMappings} from 'modules/bpmn-js/utils/getInputOutputMappings';
import {Content} from './styled';

const INFORMATION_TEXT = {
  Input:
    'Input mappings are defined while modelling the diagram. They are used to create new local variables inside the flow node scope with the specified assignment.',
  Output:
    'Output mappings are defined while modelling the diagram. They are used to control the variable propagation from the flow node scope. Process variables in the parent scopes are created/updated with the specified assignment.',
};

type Props = {
  type: 'Input' | 'Output';
};

const InputOutputMappings: React.FC<Props> = observer(({type}) => {
  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;

  if (flowNodeId === undefined) {
    return null;
  }

  const businessObject =
    processInstanceDetailsDiagramStore.businessObjects[flowNodeId];

  const mappings =
    businessObject === undefined ? [] : getMappings(businessObject, type);

  return (
    <Content>
      <IOMappingInfoBanner type={type} text={INFORMATION_TEXT[type]} />
      {mappings.length === 0 ? (
        <EmptyPanel type="info" label={`No ${type} Mappings defined`} />
      ) : (
        <DataTable
          headerColumns={[
            {
              cellContent:
                type === 'Input'
                  ? 'Local Variable Name'
                  : 'Process Variable Name',
              width: '218px',
            },
            {
              cellContent: 'Variable Assignment Value',
            },
          ]}
          rows={mappings.map(({source, target}) => {
            return {
              id: target,
              columns: [
                {
                  id: 'target',
                  cellContent: target,
                  isBold: true,
                },
                {id: 'source', cellContent: source},
              ],
            };
          })}
        />
      )}
    </Content>
  );
});

export {InputOutputMappings};
