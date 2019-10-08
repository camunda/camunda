/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default class Menu extends React.Component {
  static propTypes = {
    onKeyDown: PropTypes.func.isRequired,
    /** This defines if the dropdown opens to the top or bottom.*/
    placement: PropTypes.oneOf(['top', 'bottom']),
    /** The options of this dropdown. Each child should be a `Dropdown.Option` instance */
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    transitionTiming: PropTypes.object,
    className: PropTypes.string
  };

  render() {
    const {
      onKeyDown,
      placement,
      children,
      transitionTiming,
      className
    } = this.props;

    return (
      <Styled.Ul
        placement={placement}
        className={className}
        transitionTiming={transitionTiming}
        data-test="menu"
      >
        {React.Children.map(children, (child, index) => (
          <Styled.Li onKeyDown={onKeyDown} placement={placement} key={index}>
            {child}
          </Styled.Li>
        ))}
      </Styled.Ul>
    );
  }
}
