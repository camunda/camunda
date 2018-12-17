import {get, del, put, post} from 'request';
import {reportConfig, getDataKeys} from 'services';

const {isAllowed, getNext} = reportConfig;

export async function loadSingleReport(id) {
  const response = await get('api/report/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`api/report/${id}?force=true`);
}

export async function loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersion) {
  const response = await get('api/process-definition/xml', {
    processDefinitionKey,
    processDefinitionVersion
  });

  return await response.text();
}

export async function loadVariables(processDefinitionKey, processDefinitionVersion) {
  const response = await get('api/variables', {
    processDefinitionKey,
    processDefinitionVersion,
    namePrefix: '',
    sortOrder: 'asc',
    orderBy: 'name'
  });

  return await response.json();
}

export async function isSharingEnabled() {
  const response = await get(`api/share/isEnabled`);
  const json = await response.json();
  return json.enabled;
}

export async function getReportData(query) {
  let response;

  try {
    if (typeof query !== 'object') {
      // evaluate saved report
      response = await get(`api/report/${query}/evaluate`);
    } else {
      // evaluate unsaved report
      response = await post(`api/report/evaluate/`, query);
    }
  } catch (e) {
    return null;
  }

  return await response.json();
}

export async function saveReport(id, data, forceUpdate) {
  return await put(`api/report/${id}?force=${forceUpdate}`, data);
}

export async function shareReport(reportId) {
  const body = {
    reportId
  };
  const response = await post(`api/share/report`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedReport(reportId) {
  const response = await get(`api/share/report/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeReportSharing(id) {
  return await del(`api/share/report/${id}`);
}

export const isRawDataReport = (report, data) => {
  return (
    data &&
    data.view &&
    data.view.operation === 'rawData' &&
    report &&
    report.result &&
    report.result[0]
  );
};

export async function loadDecisionDefinitions() {
  const response = await get('api/decision-definition/groupedByKey');

  return await response.json();
}

export function isChecked(data, current) {
  return (
    current &&
    getDataKeys(data).every(
      prop =>
        JSON.stringify(current[prop]) === JSON.stringify(data[prop]) || Array.isArray(data[prop])
    )
  );
}

export function update({type, data, view, groupBy, visualization, callback}) {
  const update = {
    [type]: data
  };

  const config = {
    view,
    groupBy,
    visualization,
    ...update
  };

  const nextGroup = getNext(config.view);
  if (nextGroup) {
    config.groupBy = nextGroup;
    update.groupBy = nextGroup;
  }

  const nextVis = getNext(config.view, config.groupBy);
  if (nextVis) {
    config.visualization = nextVis;
    update.visualization = nextVis;
  }
  if (!isAllowed(config.view, config.groupBy)) {
    update.groupBy = null;
    update.visualization = null;
  } else if (!isAllowed(config.view, config.groupBy, config.visualization)) {
    update.visualization = null;
  }

  callback(update);
}
