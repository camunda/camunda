import React from 'react';

import {getHeatmap} from './service';
import Tooltip from './Tooltip';

export default class HeatmapOverlay extends React.Component {

  heatmap = undefined;

  render() {
    return <Tooltip viewer={this.props.viewer} data={this.props.data} formatter={this.props.formatter} />;
  }

  componentDidMount() {
    this.renderHeatmap();
  }

  componentDidUpdate(prevProps) {
    this.renderHeatmap();
  }

  componentWillUnmount() {
    const {viewer} = this.props;
    if(this.heatmap) {
      viewer.get('canvas')._viewport.removeChild(this.heatmap);
    }
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
