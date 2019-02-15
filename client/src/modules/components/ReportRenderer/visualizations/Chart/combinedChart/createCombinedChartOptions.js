import {generateLegendLabels} from './service';
import {formatTooltip, getTooltipLabelColor} from '../service';
import {createBarOptions} from '../defaultChart/createDefaultChartOptions';

export default function createCombinedChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, configuration},
    result
  } = report;

  const isDark = theme === 'dark';
  const stacked = visualization === 'number';
  const property = Object.values(result)[0].data.view.property;
  const instanceCountArr = Object.values(result).map(report => report.processInstanceCount);
  const maxDuration = property === 'duration' ? findMaxDurationAcrossReports(result) : 0;

  return {
    ...createBarOptions(targetValue, configuration, stacked, maxDuration, isDark),
    legend: {
      display: true,
      labels: {
        generateLabels: generateLegendLabels
      },
      // prevent hiding datasets when clicking on their legends
      onClick: e => e.stopPropagation()
    },
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    tooltips: {
      callbacks: {
        label: (tooltipItem, data) => {
          return formatTooltip(
            tooltipItem,
            data,
            targetValue,
            configuration,
            formatter,
            instanceCountArr,
            property,
            visualization
          );
        },
        labelColor: (tooltipItem, chart) => getTooltipLabelColor(tooltipItem, chart, visualization)
      }
    }
  };
}

function findMaxDurationAcrossReports(result) {
  const reportsMaxDurations = Object.values(result).map(report => {
    if (typeof report.result === 'number') return report.result;
    return Math.max(...Object.values(report.result));
  });

  return Math.max(...reportsMaxDurations);
}
