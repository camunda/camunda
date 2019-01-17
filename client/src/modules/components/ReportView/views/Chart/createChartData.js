import {createColors} from './colorsUtils';
import {createDatasetOptions, getTargetLineOptions} from './createChartOptions';
import {uniteResults} from '../service';

export default function createChartData({
  data,
  reportsNames,
  type,
  targetValue,
  configuration: {color},
  combined,
  theme,
  isDate
}) {
  const isDark = theme === 'dark';
  let dataArr = combined ? data : [data];

  const datasetsColors = color || createColors(dataArr.length, isDark);
  let labels = Object.keys(Object.assign({}, ...dataArr));
  dataArr = uniteResults(dataArr, labels);

  if (type === 'line' && targetValue.active) {
    return {
      labels,
      datasets: combined
        ? createCombinedTargetLineDatasets(
            dataArr,
            reportsNames,
            targetValue,
            datasetsColors,
            isDark
          )
        : createSingleTargetLineDataset(targetValue, dataArr[0], datasetsColors[0], false, isDark)
    };
  }

  if (isDate)
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });

  const datasets = dataArr.map((report, index) => {
    return {
      label: reportsNames && reportsNames[index],
      data: Object.values(report),
      ...createDatasetOptions(type, report, targetValue, datasetsColors[index], combined, isDark)
    };
  });

  return {
    labels,
    datasets
  };
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

function createSingleTargetLineDataset(
  targetValue,
  data,
  datasetColor,
  reportName,
  isCombined,
  isDark
) {
  const allValues = Object.values(data);
  const {targetOptions, normalLineOptions} = getTargetLineOptions(
    datasetColor,
    targetValue.values.isBelow,
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
