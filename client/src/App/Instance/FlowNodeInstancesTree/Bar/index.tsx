/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {TYPE} from 'modules/constants';
import {TimeStampLabel} from '../TimeStampLabel';
import {Container, NodeIcon, NodeName} from './styled';

type Props = {
  node?: {
    id: string;
    name?: string;
    type: string;
    typeDetails: any;
    endDate?: string;
    children?: any[];
  };
  isSelected: boolean;
};

const Bar = ({node, isSelected}: Props) => {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'typeDetails' does not exist on type '{ i... Remove this comment to see the full error message
  const {typeDetails, type, children, name, endDate} = node;

  return (
    <Container showSelectionStyle={isSelected}>
      <NodeIcon
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

export {Bar};
