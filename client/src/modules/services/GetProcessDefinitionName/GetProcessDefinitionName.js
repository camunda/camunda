export default function extractProcessDefinitionName(processDefinitionXml) {
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(processDefinitionXml, 'text/xml');

  const procesNode =
    xmlDoc.getElementsByTagName('bpmn:process')[0] || xmlDoc.getElementsByTagName('process')[0];
  return procesNode.getAttribute('name') || '';
}
