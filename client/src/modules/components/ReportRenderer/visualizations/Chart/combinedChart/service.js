import {formatters} from 'services';

const {formatReportResult} = formatters;

// Override the default generate legend's labels function
// This is done to modify the colors retrieval method of the legend squares and filter unneeded labels
export function generateLegendLabels(chart) {
  const data = chart.data;
  return data.datasets.length
    ? data.datasets
        .map(function(dataset) {
          return {
            text: dataset.label,
            fillStyle: !dataset.backgroundColor.length
              ? dataset.backgroundColor
              : dataset.legendColor,
            strokeStyle: dataset.legendColor
          };
        }, this)
        .filter(dataset => {
          return dataset.text;
        })
    : [];
}

export function getCombinedChartProps(result, data) {
  if (data.visualization === 'number')
    return {
      resultArr: getCombinedNumberData(result),
      reportsNames: Object.values(result).map(report => report.name)
    };
  return data.reportIds.reduce(
    (prev, reportId) => {
      return {
        resultArr: [...prev.resultArr, formatReportResult(data, result[reportId].result)],
        reportsNames: [...prev.reportsNames, result[reportId].name]
      };
    },
    {resultArr: [], reportsNames: []}
  );
}

function getCombinedNumberData(result) {
  return Object.values(result).map(report => ({
    [report.name]: report.result
  }));
}
