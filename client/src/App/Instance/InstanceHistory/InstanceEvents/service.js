/**
 * @returns {Array}
 * [
 *  {...event},
 *  {
 *    name,
 *    events: [{...event}]
 *  }
 * ]
 */
export function getGroupedEvents({events, activitiesDetails}) {
  let groupedEvents = [];

  // create a map of activityInstanceId -> {name}
  let activityInstancesEvents = activitiesDetails.reduce(
    (map, {id, name}) => ({...map, [id]: {name}}),
    {}
  );

  events.forEach(event => {
    // if it doesn't have an activityInstanceId it doesn't belong to an activity events group
    if (!event.activityInstanceId) {
      return groupedEvents.push(event);
    }

    const targetActivityInstance =
      activityInstancesEvents[event.activityInstanceId];

    if (!targetActivityInstance.events) {
      targetActivityInstance.events = [event];
      return groupedEvents.push(targetActivityInstance);
    }

    targetActivityInstance.events.push(event);
  });

  return groupedEvents;
}
