/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {getMappings} from 'modules/bpmn-js/utils/getInputOutputMappings';
import {Content, EmptyMessage} from './styled';
import {IOMappingInfoBanner} from './IOMappingInfoBanner';
import {useState} from 'react';
import {getStateLocally} from 'modules/utils/localStorage';
import {StructuredList} from 'modules/components/StructuredList';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

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
  const [isInfoBannerVisible, setIsInfoBannerVisible] = useState(
    !getStateLocally()?.[`hide${type}MappingsHelperBanner`],
  );

  if (flowNodeId === undefined) {
    return null;
  }

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({processDefinitionKey});
  const businessObject = data?.businessObjects[flowNodeId];

  const mappings =
    businessObject === undefined ? [] : getMappings(businessObject, type);

  return (
    <Content $isInfoBannerVisible={isInfoBannerVisible}>
      {isInfoBannerVisible && (
        <IOMappingInfoBanner
          type={type}
          text={INFORMATION_TEXT[type]}
          onClose={() => {
            setIsInfoBannerVisible(false);
          }}
        />
      )}
      {mappings.length === 0 ? (
        <EmptyMessage message={`No ${type} Mappings defined`} />
      ) : (
        <StructuredList
          label="Input Mappings"
          headerSize="sm"
          headerColumns={[
            {
              cellContent:
                type === 'Input'
                  ? 'Local Variable Name'
                  : 'Process Variable Name',
            },
            {
              cellContent: 'Variable Assignment Value',
            },
          ]}
          rows={mappings.map(({source, target}) => {
            return {
              id: target,
              key: source,
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
