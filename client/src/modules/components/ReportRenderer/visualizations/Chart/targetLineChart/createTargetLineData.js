import {getTargetLineOptions} from './createTargetLineOptions';
import {extractCombinedData} from '../combinedChart/createCombinedChartData';
import {extractDefaultChartData} from '../defaultChart/createDefaultChartData';

export default function createTargetLineData(props) {
  const {combined} = props.report;

  if (combined) {
    return createCombinedTargetLineData(props);
  } else {
    return createSingleTargetLineData(props);
  }
}

function createSingleTargetLineData(props) {
  const {labels, formattedResult, targetValue, color, isDark} = extractDefaultChartData(props);

  const datasets = createSingleTargetLineDataset(
    targetValue,
    formattedResult,
    color,
    false,
    isDark
  );

  return {labels, datasets};
}

function createCombinedTargetLineData(props) {
  const {
    labels,
    unitedResults,
    reportsNames,
    reportColors,
    targetValue,
    isDark
  } = extractCombinedData(props);

  const datasets = unitedResults.reduce((prevDataset, report, i) => {
    return [
      ...prevDataset,
      ...createSingleTargetLineDataset(
        targetValue,
        report,
        reportColors[i],
        reportsNames[i],
        true,
        isDark
      )
    ];
  }, []);

  return {labels, datasets};
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
