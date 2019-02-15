import {getColorFor} from './colorsUtils';

export function formatTooltip(
  {index, datasetIndex},
  {datasets},
  targetValue,
  configuration,
  formatter,
  processInstanceCountData,
  property,
  type
) {
  const {hideAbsoluteValue, hideRelativeValue} = configuration;
  let formatted = '';
  if (!hideAbsoluteValue) formatted = formatter(datasets[datasetIndex].data[index]);

  if (property === 'frequency' && processInstanceCountData && !hideRelativeValue) {
    let processInstanceCount = processInstanceCountData[datasetIndex];
    // in the case of the line with target value we have 2 datasets for each report
    // we have to divide by 2 to get the right index
    if (type === 'line' && targetValue) {
      processInstanceCount = processInstanceCountData[~~(datasetIndex / 2)];
    }
    return `${formatted} (${getRelativeValue(
      datasets[datasetIndex].data[index],
      processInstanceCount
    )})`;
  } else {
    return formatted;
  }
}

function getRelativeValue(data, total) {
  if (data === null) return '';
  return Math.round((data / total) * 1000) / 10 + '%';
}

export function getTooltipLabelColor(tooltipItem, chart, type) {
  const datasetOptions = chart.data.datasets[tooltipItem.datasetIndex];
  if (type === 'pie') {
    const color = datasetOptions.backgroundColor[tooltipItem.index];
    return {
      borderColor: color,
      backgroundColor: color
    };
  }

  return {
    borderColor: datasetOptions.legendColor,
    backgroundColor: datasetOptions.legendColor
  };
}

export function drawHorizentalLine(chart) {
  if (chart.options.lineAt >= 0 && chart.options.lineAt !== false) {
    const ctx = chart.chart.ctx;
    const xAxe = chart.scales[chart.options.scales.xAxes[0].id];
    const lineAt = calculateLinePosition(chart);

    ctx.save();
    ctx.strokeStyle = getColorFor('targetBar', true);
    ctx.setLineDash([10, 10]);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xAxe.left, lineAt);
    ctx.lineTo(xAxe.right, lineAt);
    ctx.stroke();
    ctx.restore();
  }
}

export function calculateLinePosition(chart) {
  const yAxis = chart.scales[chart.options.scales.yAxes[0].id];

  return (1 - chart.options.lineAt / yAxis.max) * yAxis.height + yAxis.top;
}

export function isDate(groupBy) {
  return (
    groupBy.type === 'startDate' || (groupBy.type === 'variable' && groupBy.value.type === 'Date')
  );
}
