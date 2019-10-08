/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

export default class GlobalCss extends React.Component {
  state = {
    tabKeyPressed: false
  };

  componentDidMount() {
    document.body.addEventListener('keydown', this.onKeyPressed, true);
    document.body.addEventListener('mousedown', this.onMousePressed, true);
  }

  componentWillUnmount() {
    document.body.removeEventListener('keydown', this.onKeyPressed, true);
    document.body.removeEventListener('mousedown', this.onMousePressed, true);
  }

  onKeyPressed = event => {
    // if it's tab key
    if (event.keyCode === 9) {
      this.setState({
        tabKeyPressed: true
      });
    }
  };

  onMousePressed = event => {
    this.setState({
      tabKeyPressed: false
    });
  };

  render() {
    return <Styled.GlobalStyles tabKeyPressed={this.state.tabKeyPressed} />;
  }
}
