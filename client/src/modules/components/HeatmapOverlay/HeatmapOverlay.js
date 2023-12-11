/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {getHeatmap} from './service';
import Tooltip from './Tooltip';

import './HeatmapOverlay.scss';

export default class HeatmapOverlay extends React.Component {
  heatmap = undefined;

  render() {
    if (this.props.formatter) {
      return (
        <Tooltip
          {...this.props.tooltipOptions}
          viewer={this.props.viewer}
          data={this.props.data}
          formatter={this.props.formatter}
        />
      );
    }
    return null;
  }

  componentDidMount() {
    const {viewer, onNodeClick} = this.props;
    this.renderHeatmap();

    if (onNodeClick) {
      viewer.get('eventBus').on('element.click', onNodeClick);
      this.indicateClickableNodes();
    }
  }

  indicateClickableNodes = () => {
    if (this.props.data) {
      Object.keys(this.props.data).forEach((id) => {
        const node = document.body.querySelector(`[data-element-id=${id}]`);
        node?.classList.add('clickable');
      });
    }
  };

  componentDidUpdate() {
    this.renderHeatmap();
    this.indicateClickableNodes();
  }

  componentWillUnmount() {
    const {viewer} = this.props;
    if (this.heatmap) {
      viewer.get('canvas')._viewport.removeChild(this.heatmap);
    }
  }

  renderHeatmap = () => {
    const {viewer, data, noSequenceHighlight} = this.props;

    const heatmap = getHeatmap(viewer, data, noSequenceHighlight);

    if (this.heatmap) {
      viewer.get('canvas')._viewport.removeChild(this.heatmap);
    }
    viewer.get('canvas')._viewport.appendChild(heatmap);
    this.heatmap = heatmap;
  };
}
