export function getUrl(bpmnProcessId, version) {
  const urlVersion = version.toString().includes(',') ? 'all' : version;
  return `/instances?filter={"workflow":"${bpmnProcessId}","version":"${urlVersion}","incidents":true}`;
}

export function getTitle(workflowName, version, incidentsCount) {
  const isOneVersion = !version.toString().includes(',');
  return `View ${incidentsCount} Instances with Incidents in version${
    isOneVersion ? '' : 's'
  } ${version} of Workflow ${workflowName}`;
}
