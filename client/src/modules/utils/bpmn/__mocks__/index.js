import {createDiagramNodes, createDefinitions} from 'modules/testUtils';

const bpmnElements = createDiagramNodes();

export const parsedDiagram = {bpmnElements, definitions: createDefinitions()};

export const parseDiagramXML = jest.fn(async xml => {
  return {bpmnElements, definitions: createDefinitions()};
});
