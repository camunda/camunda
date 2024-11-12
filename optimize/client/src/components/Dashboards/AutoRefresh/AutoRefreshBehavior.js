/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

export default class AutoRefreshBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    if (this.props.interval) {
      this.timer = setInterval(this.props.loadTileData, this.props.interval);
    }
  }

  componentWillUnmount() {
    clearInterval(this.timer);
  }

  componentDidUpdate(prevProps) {
    if (prevProps.interval !== this.props.interval) {
      clearInterval(this.timer);
      if (this.props.interval) {
        this.timer = setInterval(this.props.loadTileData, this.props.interval);
      }
    }
  }
}
