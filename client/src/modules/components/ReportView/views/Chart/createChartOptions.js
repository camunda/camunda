import {getColorFor, createColors, determineBarColor} from './colorsUtils';
import {
  createDurationFormattingOptions,
  getFormattedTargetValue,
  generateLegendLabels,
  formatTooltip,
  getTooltipLabelColor
} from './service';

export default function createChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, view, configuration},
    result,
    processInstanceCount,
    combined
  } = report;

  const isDark = theme === 'dark';
  const stacked = visualization === 'number';
  const property = combined ? Object.values(result)[0].data.view.property : view.property;
  const processInstanceCountArr = combined
    ? Object.values(result).map(report => report.processInstanceCount)
    : [processInstanceCount];

  let options;
  switch (visualization) {
    case 'pie':
      options = createPieOptions(isDark);
      break;
    case 'line':
    case 'bar':
    case 'number':
      options = createBarOptions(
        result,
        targetValue,
        configuration,
        property,
        stacked,
        combined,
        isDark
      );
      break;
    default:
      options = {};
  }

  return {
    ...options,
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    tooltips: {
      callbacks: {
        // if pie chart then manually append labels to tooltips
        ...(visualization === 'pie' ? {beforeLabel: ({index}, {labels}) => labels[index]} : {}),
        label: (tooltipItem, data) => {
          return formatTooltip(
            tooltipItem,
            data,
            targetValue,
            configuration,
            formatter,
            processInstanceCountArr,
            property,
            visualization
          );
        },
        labelColor: (tooltipItem, chart) => getTooltipLabelColor(tooltipItem, chart, visualization)
      }
    }
  };
}

function createBarOptions(
  result,
  targetValue,
  configuration,
  property,
  stacked,
  isCombined,
  isDark
) {
  const targetLine = targetValue && getFormattedTargetValue(targetValue);
  return {
    ...(configuration.pointMarkers === false ? {elements: {point: {radius: 0}}} : {}),
    legend: {
      display: isCombined,
      labels: {
        generateLabels: generateLegendLabels
      },
      // prevent hiding datasets when clicking on their legends
      onClick: e => e.stopPropagation()
    },
    scales: {
      yAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark)
          },
          scaleLabel: {
            display: !!configuration.yLabel,
            labelString: configuration.yLabel
          },
          ticks: {
            ...(property === 'duration'
              ? createDurationFormattingOptions(result, targetLine, isCombined)
              : {}),
            beginAtZero: true,
            fontColor: getColorFor('label', isDark),
            suggestedMax: targetLine
          }
        }
      ],
      xAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark)
          },
          scaleLabel: {
            display: !!configuration.xLabel,
            labelString: configuration.xLabel
          },
          ticks: {
            fontColor: getColorFor('label', isDark)
          },
          stacked
        }
      ]
    },
    // plugin property
    lineAt: targetLine
  };
}

function createPieOptions(isDark) {
  return {
    legend: {
      display: true,
      labels: {fontColor: getColorFor('label', isDark)}
    }
  };
}

export function createDatasetOptions(type, data, targetValue, datasetColor, isCombined, isDark) {
  switch (type) {
    case 'pie':
      return {
        borderColor: getColorFor('border', isDark),
        backgroundColor: createColors(Object.keys(data).length, isDark),
        borderWidth: undefined
      };
    case 'line':
      return {
        borderColor: datasetColor,
        backgroundColor: 'transparent',
        borderWidth: 2,
        legendColor: datasetColor
      };
    case 'bar':
    case 'number':
      const barColor = targetValue
        ? determineBarColor(targetValue, data, datasetColor, isCombined, isDark)
        : datasetColor;
      return {
        borderColor: barColor,
        backgroundColor: barColor,
        legendColor: datasetColor,
        borderWidth: 1
      };
    default:
      return {
        borderColor: undefined,
        backgroundColor: undefined,
        borderWidth: undefined
      };
  }
}

export function getTargetLineOptions(color, isBelowTarget, isCombined, isDark) {
  return {
    targetOptions: {
      borderColor: isCombined ? color : getColorFor('targetBar', isDark),
      pointBorderColor: getColorFor('targetBar', isDark),
      backgroundColor: 'transparent',
      legendColor: color,
      borderWidth: 2,
      renderArea: isBelowTarget ? 'bottom' : 'top'
    },
    normalLineOptions: {
      borderColor: color,
      backgroundColor: 'transparent',
      legendColor: color,
      borderWidth: 2,
      renderArea: isBelowTarget ? 'top' : 'bottom'
    }
  };
}
