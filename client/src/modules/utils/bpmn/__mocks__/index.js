import {createDiagramNodes, createDefinitions} from 'modules/testUtils';

export async function parseDiagramXML(xml) {
  return {bpmnElements: createDiagramNodes(), definitions: createDefinitions};
}
