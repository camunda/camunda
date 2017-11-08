import React from 'react';

import {getHeatmap} from './service';

export default class HeatmapOverlay extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    const {viewer, data} = this.props;

    const heatmap = getHeatmap(viewer, data);

    viewer.get('canvas')._viewport.appendChild(heatmap);
  }
}
