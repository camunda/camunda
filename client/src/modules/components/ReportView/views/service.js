import {get} from 'request';

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
