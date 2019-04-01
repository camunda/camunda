/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [OPERATION_TYPE.RESOLVE_INCIDENT]: <Styled.RetryIcon />,
  [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: <Styled.CancelIcon />
};

export default function ActionItems(props) {
  return (
    <Styled.Ul {...props}>{React.Children.toArray(props.children)}</Styled.Ul>
  );
}

ActionItems.propTypes = {
  children: PropTypes.node.isRequired
};

ActionItems.Item = function Item({title, onClick, type, ...rest}) {
  return (
    iconsMap.hasOwnProperty(type) && (
      <Styled.Li>
        <Styled.Button {...rest} type={type} title={title} onClick={onClick}>
          {iconsMap[type]}
        </Styled.Button>
      </Styled.Li>
    )
  );
};

ActionItems.Item.propTypes = {
  type: PropTypes.oneOf(Object.keys(OPERATION_TYPE)).isRequired,
  onClick: PropTypes.func,
  title: PropTypes.string
};
