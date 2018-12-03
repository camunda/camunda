import moment from 'moment';
import {reportConfig} from 'services';

const {view, groupBy, getLabelFor} = reportConfig;

export function getTableProps(combined, result, data, processInstanceCount) {
  if (combined) {
    return getCombinedProps(result);
  }
  const viewLabel = getLabelFor(view, data.view);
  const groupByLabel = getLabelFor(groupBy, data.groupBy);
  const formattedResult = formatResult(data, result);
  return {
    data: formattedResult,
    labels: [groupByLabel, viewLabel],
    processInstanceCount
  };
}

export function getChartProps(combined, result, data, processInstanceCount) {
  if (combined) {
    const groupBy = data.groupBy;
    const isDate =
      groupBy.type === 'startDate' ||
      (groupBy.type === 'variable' && groupBy.value.type === 'Date');

    const resultData = Object.keys(result).reduce(
      (prev, reportId) => {
        return {
          data: [...prev.data, formatResult(data, result[reportId].result)],
          reportsNames: [...prev.reportsNames, result[reportId].name],
          processInstanceCount: [
            ...prev.processInstanceCount,
            result[reportId].processInstanceCount
          ]
        };
      },
      {data: [], reportsNames: [], processInstanceCount: []}
    );
    return {
      isDate,
      ...resultData
    };
  }
  return {
    data: formatResult(data, result),
    processInstanceCount
  };
}

function getCombinedProps(result) {
  const reports = Object.keys(result).map(reportId => result[reportId]);
  const initialData = {
    labels: [],
    reportsNames: [],
    data: [],
    processInstanceCount: []
  };

  const combinedProps = reports.reduce((prevReport, report) => {
    const {data, result, processInstanceCount, name} = report;

    // build 2d array of all labels
    const viewLabel = getLabelFor(view, data.view);
    const groupByLabel = getLabelFor(groupBy, data.groupBy);
    const labels = [...prevReport.labels, [groupByLabel, viewLabel]];

    // 2d array of all names
    const reportsNames = [...prevReport.reportsNames, name];

    // 2d array of all results
    const formattedResult = formatResult(data, result);
    const reportsResult = [...prevReport.data, formattedResult];

    // 2d array of all process instances count
    const reportsProcessInstanceCount = [...prevReport.processInstanceCount, processInstanceCount];

    return {
      labels,
      reportsNames,
      data: reportsResult,
      processInstanceCount: reportsProcessInstanceCount
    };
  }, initialData);

  return combinedProps;
}

export function formatResult(data, result) {
  const groupBy = data.groupBy;
  let unit = groupBy.unit;
  if (!unit && groupBy.type === 'startDate') unit = groupBy.value.unit;
  else if (!unit && groupBy.type === 'variable' && groupBy.value.type === 'Date') unit = 'second';

  if (!unit || !result || data.view.operation === 'rawData') {
    // the result data is no time series
    return result;
  }
  const dateFormat = getDateFormat(unit);
  const formattedResult = {};
  Object.keys(result)
    .sort((a, b) => {
      // sort descending for tables and ascending for all other visualizations
      if (data.visualization === 'table') {
        return a < b ? 1 : -1;
      } else {
        return a < b ? -1 : 1;
      }
    })
    .forEach(key => {
      const formattedDate = moment(key).format(dateFormat);
      formattedResult[formattedDate] = result[key];
    });
  return formattedResult;
}

function getDateFormat(unit) {
  let dateFormat;
  switch (unit) {
    case 'hour':
      dateFormat = 'YYYY-MM-DD HH:00:00';
      break;
    case 'day':
    case 'week':
      dateFormat = 'YYYY-MM-DD';
      break;
    case 'month':
      dateFormat = 'MMM YYYY';
      break;
    case 'year':
      dateFormat = 'YYYY';
      break;
    case 'second':
    default:
      dateFormat = 'YYYY-MM-DD HH:mm:ss';
  }
  return dateFormat;
}
