import {withSelector, DESTROY_EVENT} from 'view-utils';
import * as d3 from 'd3';
import d3Tip from 'd3-tip';
d3.tip = d3Tip;

export const Chart = withSelector(({config}) => {
  return (parentNode, eventsBus) => {
    const svgNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg');

    svgNode.setAttribute('width', parentNode.clientWidth);
    svgNode.setAttribute('height', parentNode.clientHeight);

    parentNode.appendChild(svgNode);

    const svg = d3.select(svgNode);
    const margin = {
      top: config.marginTop || 20,
      right: config.marginRight || 20,
      bottom: config.marginBottom || 30,
      left: config.marginLeft || 40
    };
    const width = +svg.attr('width') - margin.left - margin.right;
    const height = +svg.attr('height') - margin.top - margin.bottom;

    const {x, y} = createScales(width, height);

    const container = svg.append('g')
        .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

    const {xAxis, yAxis} = createAxes(container, height);

    const tooltip = createTooltip(svg);

    return (data) => {
      x.domain(data.map(function(d) { return d.key; }));
      y.domain([0, d3.max(data, function(d) { return d.value; })]);

      xAxis.call(d3
        .axisBottom(x));
      yAxis.call(d3
        .axisLeft(y)
        .ticks(5, config.absoluteScale ? 'd' : '%')
        .tickSizeInner(-width));

      const bar = container.selectAll('.bar')
        .data(data);

      applyData(bar);

      const newBar = bar.enter()
        .append('rect')
        .attr('class', 'bar')
        .on('mouseover', tooltip.show)
        .on('mouseout', tooltip.hide);

      applyData(newBar);

      bar.exit().remove();
    };

    function applyData(bar) {
      bar.attr('x', function(d) { return x(d.key); })
        .attr('y', function(d) { return y(d.value); })
        .attr('width', x.bandwidth())
        .attr('height', function(d) { return height - y(d.value); });
    }

    function createTooltip(svg) {
      const tooltip = d3.tip()
        .attr('class', 'd3-tip')
        .offset([-10, 0])
        .html(function(d) {
          return d.tooltip;
        });

      svg.call(tooltip);
      eventsBus.on(DESTROY_EVENT, () => {
        tooltip.destroy();
      });

      return tooltip;
    }
  };
});

function createScales(width, height) {
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

function createAxes(container, height) {
  return {
    xAxis: container.append('g')
      .attr('class', 'axis axis-x')
      .attr('transform', 'translate(0,' + height + ')'),
    yAxis: container.append('g')
      .attr('class', 'axis axis-y')
  };
}
