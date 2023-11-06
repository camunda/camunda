/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {Select, SelectItem} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {BottomSection, DataTable, LeftColumn} from './styled';

// TODO: use real data
const sourceFlowNodes = [
  {id: 'task1', name: 'Task 1'},
  {id: 'task2', name: 'Task 2'},
];
const targetFlowNodes = [
  {id: 'task3', name: 'Task 3'},
  {id: 'task4', name: 'Task 4'},
];

const BottomPanel: React.FC = observer(() => {
  return (
    <BottomSection>
      <DataTable
        size="md"
        headers={[
          {
            header: 'Source flow nodes',
            key: 'sourceFlowNode',
            width: '50%',
          },
          {
            header: 'Target flow nodes',
            key: 'targetFlowNode',
            width: '50%',
          },
        ]}
        rows={sourceFlowNodes.map((sourceFlowNode) => {
          return {
            id: sourceFlowNode.id,
            sourceFlowNode: (
              <LeftColumn>
                <div>{sourceFlowNode.name}</div>
                <ArrowRight />
              </LeftColumn>
            ),
            targetFlowNode: (() => {
              const targetFlowNodeId =
                processInstanceMigrationStore.state.flowNodeMapping[
                  sourceFlowNode.id
                ];

              return (
                <Select
                  disabled={
                    processInstanceMigrationStore.state.currentStep ===
                    'summary'
                  }
                  size="sm"
                  hideLabel
                  labelText={`Target flow node for ${sourceFlowNode.name}`}
                  id={sourceFlowNode.id}
                  value={targetFlowNodeId}
                  onChange={({target}) => {
                    processInstanceMigrationStore.updateFlowNodeMapping({
                      sourceId: sourceFlowNode.id,
                      targetId: target.value,
                    });
                  }}
                >
                  {[{id: '', name: ''}, ...targetFlowNodes].map(
                    ({id, name}) => {
                      return <SelectItem key={id} value={id} text={name} />;
                    },
                  )}
                </Select>
              );
            })(),
          };
        })}
      />
    </BottomSection>
  );
});

export {BottomPanel};
