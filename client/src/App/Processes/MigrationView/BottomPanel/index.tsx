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
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {
  BottomSection,
  DataTable,
  ErrorMessageContainer,
  LeftColumn,
} from './styled';

const BottomPanel: React.FC = observer(() => {
  return (
    <BottomSection>
      {!processXmlMigrationSourceStore.hasSelectableFlowNodes ? (
        <ErrorMessageContainer>
          <ErrorMessage
            message="There are no mappable flow nodes"
            additionalInfo="Exit migration to select a different process"
          />
        </ErrorMessageContainer>
      ) : (
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
          onRowClick={(rowId) => {
            processInstanceMigrationStore.selectSourceFlowNode(rowId);
          }}
          checkIsRowSelected={(
            (selectedSourceFlowNodes) => (rowId) =>
              selectedSourceFlowNodes?.includes(rowId) ?? false
          )(processInstanceMigrationStore.selectedSourceFlowNodeIds)}
          rows={processXmlMigrationSourceStore.selectableFlowNodes.map(
            (sourceFlowNode) => {
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
                    ] ?? '';

                  return (
                    <Select
                      disabled={
                        processInstanceMigrationStore.state.currentStep ===
                          'summary' ||
                        !processXmlMigrationTargetStore.hasSelectableFlowNodes
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
                      {[
                        {id: '', name: ''},
                        ...processXmlMigrationTargetStore.selectableFlowNodes,
                      ].map(({id, name}) => {
                        return <SelectItem key={id} value={id} text={name} />;
                      })}
                    </Select>
                  );
                })(),
              };
            },
          )}
        />
      )}
    </BottomSection>
  );
});

export {BottomPanel};
