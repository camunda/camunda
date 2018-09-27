export default function extractProcessDefinitionName(processDefinitionXml) {
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(processDefinitionXml, 'text/xml');

  const processNode = xmlDoc.getElementsByTagNameNS('*', 'process')[0];
  return processNode ? processNode.getAttribute('name') : '' || '';
}
