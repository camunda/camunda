/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {TimeStampLabel} from '../TimeStampLabel';
import {Container, NodeIcon, NodeName, LeftContainer} from './styled';

import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {ModificationIcons} from './ModificationIcons';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  businessObject: BusinessObject;
  nodeName: string;
  isSelected: boolean;
  isBold: boolean;
  hasTopBorder: boolean;
};

const Bar: React.FC<Props> = observer(
  ({
    flowNodeInstance,
    businessObject,
    nodeName,
    isSelected,
    isBold,
    hasTopBorder,
  }) => {
    return (
      <Container $isSelected={isSelected} $hasTopBorder={hasTopBorder}>
        <LeftContainer>
          <NodeIcon
            flowNodeInstanceType={flowNodeInstance.type}
            diagramBusinessObject={businessObject}
            $isSelected={isSelected}
          />
          <NodeName isSelected={isSelected} isBold={isBold}>
            {nodeName}
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
