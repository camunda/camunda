import {ACTIVITY_STATE, UNNAMED_ACTIVITY} from 'modules/constants';

/**
 * @returns { flowNodeId : { name , type }}
 * @param {*} elementRegistry (bpmn elementRegistry)
 */
export function getFlowNodesDetails(elements) {
  const flowNodeDetails = {};

  Object.entries(elements).forEach(([id, element]) => {
    flowNodeDetails[id] = {
      name: element.name || UNNAMED_ACTIVITY,
      type: element.$type
    };
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
      state !== ACTIVITY_STATE.COMPLETED ? true : type === 'bpmn:Event';

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
