import React from 'react';

import {getHeatmap} from './service';

export default class HeatmapOverlay extends React.Component {

  heatmap = undefined;

  render() {
    return null;
  }

  componentDidMount() {
    this.renderHeatmap();
  }

  componentDidUpdate(nextProps, nextState) {
    this.renderHeatmap();
  }

  renderHeatmap = () => {
    const {viewer, data} = this.props;
    
    const heatmap = getHeatmap(viewer, data);

    if(this.heatmap) {
      viewer.get('canvas')._viewport.removeChild(this.heatmap);
    }
    viewer.get('canvas')._viewport.appendChild(heatmap);
    this.heatmap = heatmap;
  }
  
}
