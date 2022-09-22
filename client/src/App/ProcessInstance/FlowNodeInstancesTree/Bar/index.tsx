/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {TYPE} from 'modules/constants';
import {TimeStampLabel} from '../TimeStampLabel';
import {Container, NodeIcon, NodeName, LeftContainer} from './styled';

import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {FlowNodeMetaData} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {ModificationIcons} from './ModificationIcons';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  metaData: FlowNodeMetaData;
  isSelected: boolean;
  isBold: boolean;
  hasTopBorder: boolean;
};

const Bar: React.FC<Props> = observer(
  ({flowNodeInstance, metaData, isSelected, isBold, hasTopBorder}) => {
    return (
      <Container $isSelected={isSelected} $hasTopBorder={hasTopBorder}>
        <LeftContainer>
          <NodeIcon
            flowNodeInstanceType={flowNodeInstance.type}
            types={metaData.type}
            isSelected={isSelected}
            data-testid={`flow-node-icon-${metaData.type.elementType}`}
          />
          <NodeName isSelected={isSelected} isBold={isBold}>
            {`${metaData.name || flowNodeInstance.flowNodeId}${
              flowNodeInstance.type === TYPE.MULTI_INSTANCE_BODY
                ? ` (Multi Instance)`
                : ''
            }`}
          </NodeName>
          {!modificationsStore.isModificationModeEnabled && (
            <TimeStampLabel
              timeStamp={flowNodeInstance.endDate}
              isSelected={isSelected}
            />
          )}
        </LeftContainer>
        <ModificationIcons flowNodeInstance={flowNodeInstance} />
      </Container>
    );
  }
);

export {Bar};
