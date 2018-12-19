import {createDiagramNodes} from 'modules/testUtils';

export async function parseDiagramXML(xml) {
  const bpmnElements = createDiagramNodes();
  return {bpmnElements, definitions: {}};
}
