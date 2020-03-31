/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class SubOption extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node,
    ]),
    onStateChange: PropTypes.func,
    onClick: PropTypes.func,
  };

  handleOnClick = (evt) => {
    evt && evt.stopPropagation();
    this.props.onClick();
    this.props.onStateChange({isOpen: false});
  };

  render() {
    return (
      <Styled.OptionButton onClick={this.handleOnClick}>
        {this.props.children}
      </Styled.OptionButton>
    );
  }
}
