/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {addDiagramTooltip} from './service';

export default class Tooltip extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.props.viewer.get('eventBus').on('element.hover', this.renderTooltip);
    if (this.alwaysShow()) {
      this.addAllTooltips();
    }
  }

  alwaysShow = () => {
    return this.props.alwaysShowAbsolute || this.props.alwaysShowRelative;
  };

  componentDidUpdate(prevProps) {
    const {viewer, alwaysShowAbsolute, alwaysShowRelative, data} = this.props;

    if (
      prevProps.alwaysShowRelative !== alwaysShowRelative ||
      prevProps.alwaysShowAbsolute !== alwaysShowAbsolute ||
      data !== prevProps.data
    ) {
      this.removeOverlays(viewer);
      if (this.alwaysShow()) {
        this.addAllTooltips();
      }
    }
  }

  addAllTooltips = () => {
    const {viewer, formatter, data} = this.props;
    Object.keys(data).forEach(id => {
      addDiagramTooltip(viewer, id, formatter(data[id], id));
    });
  };

  componentWillUnmount() {
    this.props.viewer.get('eventBus').off('element.hover', this.renderTooltip);
    this.removeOverlays(this.props.viewer);
  }

  renderTooltip = ({element: {id}}) => {
    if (this.alwaysShow()) {
      return;
    }
    const {viewer} = this.props;

    this.removeOverlays(viewer);
    const value = this.props.data[id];
    if (value !== undefined) {
      addDiagramTooltip(viewer, id, this.props.formatter(value, id));
    }
  };

  removeOverlays = viewer => {
    viewer.get('overlays').remove({type: 'TOOLTIP'});
  };
}
