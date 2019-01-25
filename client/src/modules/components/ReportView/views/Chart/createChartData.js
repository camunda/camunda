import {createDatasetOptions, getTargetLineOptions} from './createChartOptions';
import {uniteResults} from '../service';

export default function createChartData({combined, ...props}) {
  if (combined) {
    return createCombinedChartData(props);
  } else {
    return createSingleChartData(props);
  }
}

function createCombinedChartData({
  data,
  reportsNames,
  type,
  targetValue,
  configuration: {reportColors},
  theme,
  isDate
}) {
  const isDark = theme === 'dark';

  const labels = Object.keys(Object.assign({}, ...data));
  if (isDate)
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });

  let datasets;
  if (type === 'line' && targetValue) {
    datasets = createCombinedTargetLineDatasets(
      data,
      reportsNames,
      targetValue,
      reportColors,
      isDark
    );
  } else {
    datasets = uniteResults(data, labels).map((report, index) => {
      return {
        label: reportsNames && reportsNames[index],
        data: Object.values(report),
        ...createDatasetOptions(type, report, targetValue, reportColors[index], true, isDark)
      };
    });
  }

  return {labels, datasets};
}

function createSingleChartData({data, type, targetValue, configuration: {color}, theme, isDate}) {
  const isDark = theme === 'dark';

  const labels = Object.keys(data);
  if (isDate)
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });

  let datasets;
  if (type === 'line' && targetValue) {
    datasets = createSingleTargetLineDataset(targetValue, data, color, false, isDark);
  } else {
    datasets = [
      {
        data: Object.values(data),
        ...createDatasetOptions(type, data, targetValue, color, false, isDark)
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
