import {createDatasetOptions} from '../defaultChart/createDefaultChartOptions';
import {uniteResults} from '../../service';
import {isDate} from '../service';
import {getCombinedChartProps} from './service';

export default function createCombinedChartData(props) {
  const {
    labels,
    unitedResults,
    reportsNames,
    reportColors,
    targetValue,
    isDark,
    visualization
  } = extractCombinedData(props);

  const datasets = unitedResults.map((report, index) => {
    return {
      label: reportsNames && reportsNames[index],
      data: Object.values(report),
      ...createDatasetOptions(visualization, report, targetValue, reportColors[index], true, isDark)
    };
  });

  return {labels, datasets};
}

export function extractCombinedData({report, theme, targetValue}) {
  const {result, data: combinedReportData} = report;

  const data = {...Object.values(result)[0].data, ...combinedReportData};

  const {reportsNames, resultArr, reportColors} = getCombinedChartProps(result, data);

  const isDark = theme === 'dark';

  const labels = Object.keys(Object.assign({}, ...resultArr));

  if (isDate(data.groupBy))
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });

  const unitedResults = uniteResults(resultArr, labels);

  return {
    labels,
    unitedResults,
    reportsNames,
    reportColors,
    targetValue,
    isDark,
    visualization: data.visualization
  };
}
