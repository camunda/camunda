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
    const {viewer, alwaysShow} = this.props;
    viewer.get('eventBus').on('element.hover', this.renderTooltip);
    if (alwaysShow) {
      this.addAllTooltips();
    }
  }

  componentDidUpdate() {
    const {viewer, alwaysShow} = this.props;

    this.removeOverlays(viewer);
    if (alwaysShow) {
      this.addAllTooltips();
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
    const {viewer, alwaysShow} = this.props;

    if (alwaysShow) {
      return;
    }

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
