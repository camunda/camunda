/**
 * @returns {Array} of both:
 *  (1) events that don't have any activityInstanceId
 *  (2) events grouped by activityInstanceId
 * [
 *  // (1)
 *  {...event},
 *
 *  // (2)
 *  {
 *    name,
 *    events: [{...event}]
 *  }
 * ]
 */
export function getGroupedEvents({events, activitiesDetails}) {
  let groupedEvents = [];
  // make a deep clone of the activitiesDetails object
  let activitiesEvents = JSON.parse(JSON.stringify(activitiesDetails));

  events.forEach(event => {
    // if it doesn't have an activityInstanceId it doesn't belong to an activity events group
    if (!event.activityInstanceId) {
      return groupedEvents.push(event);
    }

    const targetActivityInstance = activitiesEvents[event.activityInstanceId];

    if (!targetActivityInstance.events) {
      targetActivityInstance.events = [event];
      // this allows to only push once a reference to the targetActivityInstance object to groupedEvents
      return groupedEvents.push(targetActivityInstance);
    }

    // modifying the targetActivityInstance object will also affect the one in the array since it's a reference
    targetActivityInstance.events.push(event);
  });

  return groupedEvents;
}
