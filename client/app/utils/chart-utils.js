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
  const mouseOverCallback = onHoverChange(true);
  const mouseOutCallback = onHoverChange(false);

  const newBars = bars.enter()
    .append('rect')
    .attr('class', 'bar')
    .on('mouseover', (...args) => {
      // it only allows one callback to be registered per event
      tooltip.show(...args);
      mouseOverCallback(...args);
    })
    .on('mouseout', (...args) => {
      tooltip.hide(...args);
      mouseOutCallback(...args);
    });

  updateBars({bars: newBars, x, y, height});
}

export function removeOldBars(bars) {
  bars.exit().remove();
}

export function createChart() {
  const svgNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg');

  svgNode.setAttribute('width', '100%');
  svgNode.setAttribute('height', '100%');
  svgNode.setAttribute('viewBox', '0 0 600 300');
  svgNode.setAttribute('preserveAspectRatio', 'none');

  return d3.select(svgNode);
}

export function createContainer(svg) {
  return svg.append('g')
    .attr('transform', 'translate(40,20)');
}
