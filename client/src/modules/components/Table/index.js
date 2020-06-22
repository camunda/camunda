/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Table extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node,
    ]),
  };

  render() {
    return <Styled.Table {...this.props} />;
  }
}

Table.THead = function THead(props) {
  return <Styled.THead {...props} />;
};

Table.TBody = function TBody(props) {
  return <tbody {...props} />;
};

Table.TH = function TH(props) {
  return <Styled.TH {...props} />;
};

Table.TR = function TR(props) {
  return <Styled.TR {...props} />;
};

Table.TD = function TD(props) {
  return <Styled.TD {...props} />;
};
