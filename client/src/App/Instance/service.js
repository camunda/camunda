import {STATE, UNNAMED_ACTIVITY} from 'modules/constants';

/**
 * @returns activityId -> name map
 * @param {*} elementRegistry (bpmn elementRegistry)
 */
export function getActivityIdToNameMap(elements) {
  return Object.entries(elements).reduce((map, [id, element]) => {
    map.set(id, element.name || UNNAMED_ACTIVITY);
    return map;
  }, new Map());
}

/**
 * @returns {Array} of flow node state overlays to be added the diagram.
 * @param {*} activitiesMap
 */
export function getFlowNodeStateOverlays(activitiesMap) {
  return [...activitiesMap.entries()].reduce(
    (overlays, [id, activityInstances]) => {
      const {state, type} = activityInstances[0];

      // If the activity is completed, only push an overlay
      // if the activity is an end event.
      const shouldPushOverlay =
        state !== STATE.COMPLETED ? true : type === 'END_EVENT';

      return !shouldPushOverlay ? overlays : [...overlays, {id, state}];
    },
    []
  );
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

/**
 * transforms the activities instrances tree to
 * @return activityIdToActivityInstanceMap: (activityId -> activityInstance) map
 * @param {*} activitiesInstancesTree
 * @param {*} [activityIdToActivityInstanceMap] optional
 */
export function getActivityIdToActivityInstanceMap(
  activitiesInstancesTree,
  activityIdToActivityInstanceMap = new Map()
) {
  const {children} = activitiesInstancesTree;

  return children.reduce(
    (activityIdToActivityInstanceMap, activityInstance) => {
      const {activityId} = activityInstance;

      // update activityIdToActivityInstanceMap
      const siblingActivityInstances =
        activityIdToActivityInstanceMap.get(activityId) || [];

      activityIdToActivityInstanceMap.set(activityId, [
        ...siblingActivityInstances,
        activityInstance
      ]);

      return !activityInstance.children
        ? activityIdToActivityInstanceMap
        : getActivityIdToActivityInstanceMap(
            activityInstance,
            activityIdToActivityInstanceMap
          );
    },
    activityIdToActivityInstanceMap
  );
}
