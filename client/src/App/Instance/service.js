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
 * @param {*} activityIdToActivityInstanceMap
 */
export function getFlowNodeStateOverlays(activityIdToActivityInstanceMap) {
  return [...activityIdToActivityInstanceMap.entries()].reduce(
    (overlays, [id, activityInstancesMap]) => {
      const {state, type} = [...activityInstancesMap.values()].reverse()[0];

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
  return state === STATE.ACTIVE || state === STATE.INCIDENT;
}

/**
 * transforms the activities instrances tree to
 * @return activityIdToActivityInstanceMap: (activityId -> activityInstance) map
 * @param {*} activitiesInstancesTree
 * @param {*} [activityIdToActivityInstanceMap] optional
 */
export function getActivityIdToActivityInstancesMap(
  activitiesInstancesTree,
  activityIdToActivityInstanceMap = new Map()
) {
  const {children} = activitiesInstancesTree;

  return children.reduce(
    (activityIdToActivityInstanceMap, activityInstance) => {
      const {id, activityId} = activityInstance;

      // update activityIdToActivityInstanceMap
      const activityInstancesMap =
        activityIdToActivityInstanceMap.get(activityId) || new Map();

      activityInstancesMap.set(id, activityInstance);

      activityIdToActivityInstanceMap.set(activityId, activityInstancesMap);

      return !activityInstance.children
        ? activityIdToActivityInstanceMap
        : getActivityIdToActivityInstancesMap(
            activityInstance,
            activityIdToActivityInstanceMap
          );
    },
    activityIdToActivityInstanceMap
  );
}
