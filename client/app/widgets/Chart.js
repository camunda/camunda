import React from 'react';
import * as d3 from 'd3';
import {
  createScales, createAxes, createTooltip,
  createContainer, updateScales, updateAxes, collectBars, updateBars,
  createNewBars, removeOldBars
} from 'utils/chart-utils';
import {noop} from 'view-utils';

const jsx = React.createElement;

export const margin = {
  horizontal: 60,
  vertical: 50
};

export class Chart extends React.Component {
  componentWillUnmount() {
    this.tooltip.destroy();
  }

  componentDidMount() {
    this.container = createContainer(this.svg);

    const {width, height} = this.props;
    const innerWidth = width - margin.horizontal;
    const innerHeight = height - margin.vertical;

    this.scale = createScales(innerWidth, innerHeight);
    this.axis = createAxes(this.container, innerHeight);

    this.tooltip = createTooltip(this.svg);

    this.updateChart();
  }

  updateChart = () => {
    const {height, width} = this.props;
    const innerWidth = width - margin.horizontal;
    const innerHeight = height - margin.vertical;

    const {x, y} = this.scale;
    const {xAxis, yAxis} = this.axis;
    const {data, absoluteScale, onHoverChange} = this.props;

    y.rangeRound([innerHeight, 0]);
    xAxis.attr('transform', 'translate(0,' + innerHeight + ')');
    this.svg.attr('viewBox', '0 0 ' + width + ' ' + height);

    updateScales({data, x, y});
    updateAxes({xAxis, yAxis, x, y, scale: absoluteScale ? 'd' : '%', width:innerWidth});

    const bars = collectBars({container: this.container, data});

    updateBars({bars, x, y, height: innerHeight});
    createNewBars({bars, x, y, height: innerHeight, tooltip: this.tooltip, onHoverChange: onHoverChange || noop});
    removeOldBars(bars);
  }

  componentDidUpdate() {
    this.updateChart();
  }

  storeSVGRef = svg => {
    this.svg = d3.select(svg);
  }

  render() {
    return (
      <svg width="100%" height="100%" preserveAspectRatio="none" ref={this.storeSVGRef} />
    );
  }
}
