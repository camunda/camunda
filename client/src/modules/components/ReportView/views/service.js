import {get} from 'request';
import {formatters} from 'services';

export async function getCamundaEndpoints() {
  const response = await get('/api/camunda');
  return await response.json();
}

export function getRelativeValue(data, total) {
  if (data === null) return '';
  return Math.round(data / total * 1000) / 10 + '%';
}

export function getFormattedLabels(reportsLabels, reportsNames, isFrequency) {
  return reportsLabels.reduce(
    (prev, reportLabels, i) => [
      ...prev,
      {
        label: reportsNames[i],
        columns: [...reportLabels.slice(1), ...(isFrequency ? ['Relative Frequency'] : [])]
      }
    ],
    []
  );
}

export function uniteResults(results, allKeys) {
  const unitedResults = [];
  results.forEach(result => {
    const newResult = {};
    allKeys.forEach(key => {
      if (typeof result[key] === 'undefined') {
        newResult[key] = null;
      } else {
        newResult[key] = result[key];
      }
    });
    unitedResults.push(newResult);
  });
  return unitedResults;
}

export function getBodyRows(unitedResults, allKeys, formatter, isFrequency, processInstanceCount) {
  const rows = allKeys.map(key => {
    const row = [key];
    unitedResults.forEach((result, i) => {
      row.push(formatter(result[key] || ''));
      if (isFrequency) row.push(getRelativeValue(result[key], processInstanceCount[i]));
    });
    return row;
  });
  return rows;
}

export function seperateLineTargetValues(data, values) {
  const LineValue = values.dateFormat
    ? formatters.convertToMilliseconds(values.target, values.dateFormat)
    : values.target;

  const targetValues = [];
  const normalValues = [];
  Object.values(data).forEach(height => {
    const checkCase = values.isBelow ? height < LineValue : height >= LineValue;
    if (checkCase) {
      normalValues.push(height);
      targetValues.push(null);
    } else {
      normalValues.push(null);
      targetValues.push(height);
    }
  });
  return {
    normalValues,
    targetValues
  };
}

export function fillLineGaps(normalValues, targetValues) {
  let newNormalValues = [];
  normalValues.forEach((element, i) => {
    if (
      element === null &&
      typeof normalValues[i - 1] !== 'undefined' &&
      normalValues[i - 1] !== null
    )
      return (newNormalValues[i] = targetValues[i]);
    if (element !== null && normalValues[i - 1] === null)
      newNormalValues[i - 1] = targetValues[i - 1];
    newNormalValues.push(element);
  });

  return newNormalValues;
}
