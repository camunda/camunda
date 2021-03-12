/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {TYPE} from 'modules/constants';
import {TimeStampLabel} from '../TimeStampLabel';
import {Container, NodeIcon, NodeName} from './styled';
import {IS_NEXT_FLOW_NODE_INSTANCES} from 'modules/feature-flags';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {FlowNodeMetaData} from 'modules/stores/singleInstanceDiagram';

type LegacyProps = {
  node: {
    id: string;
    name?: string;
    type: string;
    typeDetails: any;
    endDate: string | null;
    children: any[];
  };
  isSelected: boolean;
};

type Props = {
  flowNodeInstance: FlowNodeInstance;
  metaData: FlowNodeMetaData;
  isSelected: boolean;
  isBold: boolean;
};

const Bar: React.FC<Props> = ({
  flowNodeInstance,
  metaData,
  isSelected,
  isBold,
}) => {
  return (
    <Container showSelectionStyle={isSelected}>
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

const BarLegacy = ({node, isSelected}: LegacyProps) => {
  const {typeDetails, type, children, name, endDate} = node;

  return (
    <Container showSelectionStyle={isSelected}>
      <NodeIcon
        flowNodeInstanceType={type}
        types={typeDetails}
        isSelected={isSelected}
        data-testid={`flow-node-icon-${type}`}
      />
      <NodeName isSelected={isSelected} isBold={children.length > 0}>
        {`${name ?? ''}${
          type === TYPE.MULTI_INSTANCE_BODY ? ` (Multi Instance)` : ''
        }`}
      </NodeName>
      <TimeStampLabel timeStamp={endDate} isSelected={isSelected} />
    </Container>
  );
};

const CurrentBar = IS_NEXT_FLOW_NODE_INSTANCES ? Bar : BarLegacy;

export {CurrentBar as Bar};
