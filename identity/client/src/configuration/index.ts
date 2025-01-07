const baseUrl = "/identity";

const apiBaseUrl = "/v2";

export const docsUrl = "https://docs.camunda.io";

export function getApiBaseUrl() {
  return getBaseUrl(apiBaseUrl);
}

export function getBaseUrl(path = baseUrl) {
  const uiPath = window.location.pathname;
  const endIndex = uiPath.indexOf(baseUrl);
  return uiPath.substring(0, endIndex) + path;
}
