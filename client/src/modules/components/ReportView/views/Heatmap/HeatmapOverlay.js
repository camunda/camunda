import React from 'react';

import {getHeatmap} from './service';

export default class HeatmapOverlay extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      heatmap: undefined
    };
  }

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

    if(this.state.heatmap) {
      viewer.get('canvas')._viewport.removeChild(this.state.heatmap);
    }
    viewer.get('canvas')._viewport.appendChild(heatmap);
    this.state.heatmap = heatmap;
  }
  
}
