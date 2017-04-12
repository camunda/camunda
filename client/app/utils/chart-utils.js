import * as d3 from 'd3';
import d3Tip from 'd3-tip';
d3.tip = d3Tip;

export function createTooltip(svg) {
  const tooltip = d3.tip()
    .attr('class', 'd3-tip')
    .offset([-10, 0])
    .html(function(d) {
      return d.tooltip;
    });

  svg.call(tooltip);

  return tooltip;
}

export function createScales(width, height) {
  return {
    x: d3
      .scaleBand()
      .rangeRound([0, width])
      .padding(0.1),
    y: d3
      .scaleLinear()
      .rangeRound([height, 0])
  };
}

export function createAxes(container, height) {
  return {
    xAxis: container.append('g')
      .attr('class', 'axis axis-x')
      .attr('transform', 'translate(0,' + height + ')'),
    yAxis: container.append('g')
      .attr('class', 'axis axis-y')
  };
}

export function getChartDimensions(svg, {marginTop, marginRight, marginBottom, marginLeft}) {
  const margin = {
    top: marginTop || 20,
    right: marginRight || 20,
    bottom: marginBottom || 30,
    left: marginLeft || 40
  };
  const width = +svg.attr('width') - margin.left - margin.right;
  const height = +svg.attr('height') - margin.top - margin.bottom;

  return {margin, width, height};
}

export function updateScales({data, x, y}) {
  x.domain(data.map(function({key}) { return key; }));
  y.domain([0, d3.max(data, function({value}) { return value; })]);
}

export function updateAxes({xAxis, yAxis, x, y, scale, width}) {
  xAxis.call(d3
    .axisBottom(x));
  yAxis.call(d3
    .axisLeft(y)
    .ticks(5, scale)
    .tickSizeInner(-width));
}

export function collectBars({container, data}) {
  return container.selectAll('.bar').data(data);
}

export function updateBars({bars, x, y, height}) {
  bars.attr('x', function({key}) { return x(key); })
    .attr('y', function({value}) { return y(value); })
    .attr('width', x.bandwidth())
    .attr('height', function({value}) { return height - y(value); });
}

export function createNewBars({bars, x, y, height, tooltip, onHoverChange}) {
  const newBars = bars.enter()
    .append('rect')
    .attr('class', 'bar')
    .on('mouseover', tooltip.show)
    .on('mouseover', onHoverChange(true))
    .on('mouseout', tooltip.hide)
    .on('mouseout', onHoverChange(false));

  updateBars({bars: newBars, x, y, height});
}

export function removeOldBars(bars) {
  bars.exit().remove();
}

export function createChartOn(node) {
  const svgNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg');

  svgNode.setAttribute('width', node.clientWidth);
  svgNode.setAttribute('height', node.clientHeight);

  node.appendChild(svgNode);

  return d3.select(svgNode);
}

export function createContainer(svg, {left, top}) {
  return svg.append('g')
    .attr('transform', 'translate(' + left + ',' + top + ')');
}
