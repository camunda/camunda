import {Colors, themeStyle} from 'modules/theme';
import {FLOW_NODE_TYPE, UNNAMED_ACTIVITY} from 'modules/constants';

export function getDiagramColors(theme) {
  return {
    defaultFillColor: themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04
    })({theme}),
    defaultStrokeColor: themeStyle({
      dark: Colors.darkDiagram,
      light: Colors.uiLight06
    })({theme})
  };
}

export function getElementType({businessObject, type}) {
  if (type === 'label') {
    return null;
  }
  if (businessObject.$instanceOf('bpmn:Task')) {
    return FLOW_NODE_TYPE.TASK;
  }

  if (businessObject.$instanceOf('bpmn:StartEvent')) {
    return FLOW_NODE_TYPE.START_EVENT;
  }

  if (businessObject.$instanceOf('bpmn:EndEvent')) {
    return FLOW_NODE_TYPE.END_EVENT;
  }

  if (businessObject.$instanceOf('bpmn:Gateway')) {
    return FLOW_NODE_TYPE.GATEWAY;
  }
}

/**
 * @returns { flowNodeId : { name , type }}
 * @param {*} elementRegistry (bpmn elementRegistry)
 */
export function getFlowNodesDetails(elementRegistry) {
  const flowNodeDetails = {};

  elementRegistry.forEach(element => {
    const type = getElementType(element);
    if (!!type) {
      flowNodeDetails[element.id] = {
        name: element.businessObject.name || UNNAMED_ACTIVITY,
        type
      };
    }
  });

  return flowNodeDetails;
}
