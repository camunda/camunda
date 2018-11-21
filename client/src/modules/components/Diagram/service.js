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

  if (businessObject.$instanceOf('bpmn:Event')) {
    return FLOW_NODE_TYPE.EVENT;
  }

  if (businessObject.$instanceOf('bpmn:ExclusiveGateway')) {
    return FLOW_NODE_TYPE.EXCLUSIVE_GATEWAY;
  }

  if (businessObject.$instanceOf('bpmn:ParallelGateway')) {
    return FLOW_NODE_TYPE.PARALLEL_GATEWAY;
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

export function getOverlaysByState(statistics) {
  const overlays = {
    active: [],
    completed: [],
    canceled: [],
    incidents: []
  };

  statistics.forEach(statistic => {
    const states = ['active', 'completed', 'incidents', 'canceled'];

    states.forEach(state => {
      if (statistic[state] > 0) {
        overlays[state].push({
          id: statistic.activityId,
          count: statistic[state]
        });
      }
    });
  });

  return overlays;
}
