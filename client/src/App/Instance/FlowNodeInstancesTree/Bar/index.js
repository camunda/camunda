/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {TYPE} from 'modules/constants';
import {TimeStampLabel} from '../TimeStampLabel';
import {Container, NodeIcon, NodeName} from './styled';

const Bar = ({node, isSelected}) => {
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

Bar.propTypes = {
  node: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string,
    type: PropTypes.string.isRequired,
    typeDetails: PropTypes.object.isRequired,
    endDate: PropTypes.string,
    children: PropTypes.arrayOf(PropTypes.object),
  }),
  isSelected: PropTypes.bool,
};

export {Bar};
