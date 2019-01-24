import moment from 'moment';
import {reportConfig} from 'services';

const {view, groupBy, getLabelFor} = reportConfig;

export function getCombinedChartProps(result, data, report) {
  const groupBy = data.groupBy;
  const isDate =
    groupBy.type === 'startDate' || (groupBy.type === 'variable' && groupBy.value.type === 'Date');

  const resultData = report.data.reportIds.reduce(
    (prev, reportId) => {
      return {
        data: [...prev.data, formatResult(data, result[reportId].result)],
        reportsNames: [...prev.reportsNames, result[reportId].name],
        processInstanceCount: [...prev.processInstanceCount, result[reportId].processInstanceCount]
      };
    },
    {data: [], reportsNames: [], processInstanceCount: []}
  );
  return {
    isDate,
    ...resultData
  };
}

export function getCombinedTableProps(reportResult, report) {
  const initialData = {
    labels: [],
    reportsNames: [],
    data: [],
    processInstanceCount: []
  };

  const combinedProps = report.data.reportIds.reduce((prevReport, reportId) => {
    const report = reportResult[reportId];
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
  let unit;
  if (groupBy.value && groupBy.type === 'startDate') {
    unit = determineUnit(groupBy.value.unit, result);
  } else if (groupBy.value && groupBy.type === 'variable' && groupBy.value.type === 'Date') {
    unit = 'second';
  }

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

export function isEmpty(str) {
  return !str || 0 === str.length;
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

function determineUnit(unit, resultData) {
  if (unit === 'automatic') {
    return determineUnitForAutomaticIntervalSelection(resultData);
  } else {
    // in this case the unit was already defined by the user
    // and can just directly be used.
    return unit;
  }
}

function determineUnitForAutomaticIntervalSelection(resultData) {
  const dates = Object.keys(resultData).sort((a, b) => {
    return a < b ? 1 : -1;
  });
  if (dates.length > 1) {
    const firstEntry = moment(dates[0]);
    const secondEntry = moment(dates[1]);
    const intervalInMs = firstEntry.diff(secondEntry);
    const intervals = [
      {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'year'},
      {value: 1000 * 60 * 60 * 24 * 30, unit: 'month'},
      {value: 1000 * 60 * 60 * 24, unit: 'day'},
      {value: 1000 * 60 * 60, unit: 'hour'},
      {value: 0, unit: 'second'},
      {value: -Infinity, unit: 'day'}
    ];
    return intervals.find(({value}) => intervalInMs >= value).unit;
  } else {
    return 'day';
  }
}
