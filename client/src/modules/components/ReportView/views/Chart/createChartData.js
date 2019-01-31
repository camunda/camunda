import {createDatasetOptions, getTargetLineOptions} from './createChartOptions';
import {uniteResults} from '../service';
import {formatters} from 'services';

const {formatReportResult} = formatters;

export default function createChartData({combined, ...props}) {
  if (combined) {
    return createCombinedChartData(props);
  } else {
    return createSingleChartData(props);
  }
}

function createCombinedChartData({result, data, theme, targetValue, reportsNames}) {
  const {configuration: {reportColors}} = data;

  const isDark = theme === 'dark';

  const labels = Object.keys(Object.assign({}, ...result));

  if (isDate(data.groupBy))
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });

  let datasets;
  if (data.visualization === 'line' && targetValue) {
    datasets = createCombinedTargetLineDatasets(
      result,
      reportsNames,
      targetValue,
      reportColors,
      isDark
    );
  } else {
    datasets = uniteResults(result, labels).map((report, index) => {
      return {
        label: reportsNames && reportsNames[index],
        data: Object.values(report),
        ...createDatasetOptions(
          data.visualization,
          report,
          targetValue,
          reportColors[index],
          true,
          isDark
        )
      };
    });
  }

  return {labels, datasets};
}

function createSingleChartData({result, data, theme, targetValue, flowNodeNames}) {
  const isDark = theme === 'dark';
  const {groupBy, visualization, configuration: {color}} = data;
  const formattedResult = formatReportResult(data, result);

  let labels = Object.keys(formattedResult);

  if (isDate(groupBy))
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });

  if (data.groupBy.type === 'flowNodes') {
    labels = labels.map(key => flowNodeNames[key] || key);
  }

  let datasets;
  if (visualization === 'line' && targetValue) {
    datasets = createSingleTargetLineDataset(targetValue, formattedResult, color, false, isDark);
  } else {
    datasets = [
      {
        data: Object.values(formattedResult),
        ...createDatasetOptions(visualization, formattedResult, targetValue, color, false, isDark)
      }
    ];
  }

  return {labels, datasets};
}

function createCombinedTargetLineDatasets(data, reportsNames, targetValue, datasetsColors, isDark) {
  return data.reduce((prevDataset, report, i) => {
    return [
      ...prevDataset,
      ...createSingleTargetLineDataset(
        targetValue,
        report,
        datasetsColors[i],
        reportsNames[i],
        true,
        isDark
      )
    ];
  }, []);
}

function createSingleTargetLineDataset(targetValue, data, color, reportName, isCombined, isDark) {
  const allValues = Object.values(data);
  const {targetOptions, normalLineOptions} = getTargetLineOptions(
    color,
    targetValue.isBelow,
    isCombined,
    isDark
  );

  const datasets = [
    {
      data: allValues,
      ...targetOptions
    },
    {
      label: reportName,
      data: allValues,
      ...normalLineOptions
    }
  ];

  return datasets;
}

function isDate(groupBy) {
  return (
    groupBy.type === 'startDate' || (groupBy.type === 'variable' && groupBy.value.type === 'Date')
  );
}
