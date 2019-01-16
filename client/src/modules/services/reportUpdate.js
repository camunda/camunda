import update from 'immutability-helper';
import {post} from 'request';

const handlers = [];

export function createUpdateFunction({combined, type, report, result, callback, loadingCallback}) {
  return async function(change, needsReevaluation) {
    handlers.forEach(handler => handler(report, change));
    const newReport = update(report, change);
    let newResult = update(result, {data: {...change}});

    if (needsReevaluation) {
      loadingCallback(newReport);
      newResult = await evaluateReport({combined, reportType: type, data: newReport});
    }

    callback(newReport, newResult);
  };
}

export function addChangeHandler(fct) {
  handlers.push(fct);
}

async function evaluateReport(query) {
  try {
    const response = await post('api/report/evaluate', query);
    return await response.json();
  } catch (e) {
    return query;
  }
}
