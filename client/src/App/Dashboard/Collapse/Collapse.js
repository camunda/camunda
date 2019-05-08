/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';
export default class Collapse extends React.Component {
  static propTypes = {
    content: PropTypes.node,
    header: PropTypes.node,
    buttonTitle: PropTypes.string
  };

  state = {
    isCollapsed: true
  };

  handleToggle = () => {
    this.setState(prevState => {
      return {
        isCollapsed: !prevState.isCollapsed
      };
    });
  };

  render() {
    return (
      <Styled.Collapse>
        <Styled.ExpandButton
          onClick={this.handleToggle}
          title={this.props.buttonTitle}
          isExpanded={!this.state.isCollapsed}
          expandTheme="collapse"
        />
        {this.props.header}
        {!this.state.isCollapsed && this.props.content}
      </Styled.Collapse>
    );
  }
}
