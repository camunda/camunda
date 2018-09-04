import {ACTIVITY_STATE, FLOW_NODE_TYPE} from 'modules/constants';

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
