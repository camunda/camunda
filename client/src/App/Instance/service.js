import {
  ACTIVITY_STATE,
  FLOW_NODE_TYPE,
  UNNAMED_ACTIVITY
} from 'modules/constants';

export function getElementType(element) {
  if (element.$type === 'label') {
    return null;
  }
  if (element.$instanceOf('bpmn:Task')) {
    return FLOW_NODE_TYPE.TASK;
  }

  if (element.$instanceOf('bpmn:StartEvent')) {
    return FLOW_NODE_TYPE.START_EVENT;
  }

  if (element.$instanceOf('bpmn:EndEvent')) {
    return FLOW_NODE_TYPE.END_EVENT;
  }

  if (element.$instanceOf('bpmn:Event')) {
    return FLOW_NODE_TYPE.EVENT;
  }

  if (element.$instanceOf('bpmn:ExclusiveGateway')) {
    return FLOW_NODE_TYPE.EXCLUSIVE_GATEWAY;
  }

  if (element.$instanceOf('bpmn:ParallelGateway')) {
    return FLOW_NODE_TYPE.PARALLEL_GATEWAY;
  }
}

/**
 * @returns { flowNodeId : { name , type }}
 * @param {*} elementRegistry (bpmn elementRegistry)
 */
export function getFlowNodesDetails(elements) {
  const flowNodeDetails = {};

  Object.entries(elements).forEach(([id, element]) => {
    const type = getElementType(element);
    if (!!type) {
      flowNodeDetails[id] = {
        name: element.name || UNNAMED_ACTIVITY,
        type
      };
    }
  });

  return flowNodeDetails;
}

export function getFlowNodeStateOverlays(activitiesDetails = {}) {
  // {Array} of flow node state overlays to be added the diagram.
  let flowNodeStateOverlays = [];

  // Go through activitiesDetails values to determine overlays to add.
  Object.values(activitiesDetails).forEach(activity => {
    const {state, type, activityId: id} = activity;

    // If the activity is completed, only push an overlay
    // if the activity is an end event.
    const shouldPushOverlay =
      state !== ACTIVITY_STATE.COMPLETED
        ? true
        : type === FLOW_NODE_TYPE.END_EVENT;

    if (shouldPushOverlay) {
      flowNodeStateOverlays.push({id, state});
    }
  });

  return flowNodeStateOverlays;
}

export function isRunningInstance(state) {
  return state === 'ACTIVE' || state === 'INCIDENTS';
}

export function beautifyMetadataKey(key) {
  switch (key) {
    case 'activityInstanceId':
      return 'Flow Node Instance Id';
    case 'jobId':
      return 'Job Id';
    case 'startDate':
      return 'Start Time';
    case 'endDate':
      return 'End Time';
    default:
      return key;
  }
}
