/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {TYPE} from 'modules/constants';
import {TimeStampLabel} from '../TimeStampLabel';
import {Container, NodeIcon, NodeName} from './styled';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {FlowNodeMetaData} from 'modules/stores/singleInstanceDiagram';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  metaData: FlowNodeMetaData;
  isSelected: boolean;
  isBold: boolean;
  hasTopBorder: boolean;
};

const Bar: React.FC<Props> = ({
  flowNodeInstance,
  metaData,
  isSelected,
  isBold,
  hasTopBorder,
}) => {
  return (
    <Container $isSelected={isSelected} $hasTopBorder={hasTopBorder}>
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
      <TimeStampLabel
        timeStamp={flowNodeInstance.endDate}
        isSelected={isSelected}
      />
    </Container>
  );
};

export {Bar};
