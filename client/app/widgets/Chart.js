import React from 'react';
import * as d3 from 'd3';
import {
  createScales, createAxes, createTooltip, getChartDimensions,
  createContainer, updateScales, updateAxes, collectBars, updateBars,
  createNewBars, removeOldBars
} from 'utils/chart-utils';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

class ChartReact extends React.Component {
  componentWillUnmount() {
    this.tooltip.destroy();
  }

  getParentContainer() {
    // We need to get the parent container for d3 to calculate the height of the y-axis
    // The second parentNode is introduced by the createViewUtilsComponentFromReact wrapper
    return this.svg.node().parentNode.parentNode;
  }

  componentDidMount() {
    this.container = createContainer(this.svg);

    const {width, height} = getChartDimensions(this.getParentContainer());

    this.scale = createScales(width, height);
    this.axis = createAxes(this.container, height);

    this.tooltip = createTooltip(this.svg);
  }

  componentDidUpdate() {
    const {height, width, margin} = getChartDimensions(this.getParentContainer());

    const {x, y} = this.scale;
    const {xAxis, yAxis} = this.axis;
    const {data, absoluteScale, onHoverChange} = this.props;

    y.rangeRound([height, 0]);
    xAxis.attr('transform', 'translate(0,' + height + ')');
    this.svg.attr('viewBox', '0 0 600 ' + (height + margin.top + margin.bottom));

    updateScales({data, x, y});
    updateAxes({xAxis, yAxis, x, y, scale: absoluteScale ? 'd' : '%', width});

    const bars = collectBars({container: this.container, data});

    updateBars({bars, x, y, height});
    createNewBars({bars, x, y, height, tooltip: this.tooltip, onHoverChange: onHoverChange || (() => {})});
    removeOldBars(bars);
  }

  storeSVGRef = svg => {
    this.svg = d3.select(svg);
  }

  render() {
    return (
      <svg width="100%" height="100%" viewBox="0 0 600 300" preserveAspectRatio="none" ref={this.storeSVGRef} />
    );
  }
}

export const Chart = createViewUtilsComponentFromReact('div', ChartReact);
