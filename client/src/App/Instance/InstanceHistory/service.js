import {formatDate} from 'modules/utils/date';

export function getEventLabel({eventSourceType, eventType}) {
  return eventType.includes('_')
    ? eventType
    : `${eventSourceType} ${eventType}`;
}

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
  // make a deep clone of the activitiesDetails object
  let activitiesEvents = JSON.parse(JSON.stringify(activitiesDetails));

  let groupedEvents = [];
  events.forEach(originalEvent => {
    // add label and tiestamp to the event
    const event = {
      ...originalEvent,
      label: getEventLabel(originalEvent),
      metadata: {
        ...originalEvent.metadata,
        timestamp: formatDate(originalEvent.dateTime)
      }
    };

    // if it doesn't have an activityInstanceId it doesn't belong to an activity events group
    if (!event.activityInstanceId) {
      return groupedEvents.push(event);
    }

    // eventActivityInstance = activity instance related to this event
    const eventActivityInstance = activitiesEvents[event.activityInstanceId];

    // If the eventActivityInstance doesn't have events, it means that it has not been pushed to
    // the groupedEvents yet.
    // Therefore, we add to it an events array here containing the current event and we push it to
    // the groupedEvents array.
    if (!eventActivityInstance.events) {
      eventActivityInstance.events = [event];
      return groupedEvents.push(eventActivityInstance);
    }

    // If the eventActivityInstance has events, it means it has already been pushed to the groupedEvents.
    // Therefore, we push in it the current event.
    // Notice that modifying the eventActivityInstance object will also affect the one in the array since
    // it's a reference
    eventActivityInstance.events.push(event);
  });

  return groupedEvents;
}

export function getActivityInstanceEvents({events = [], activityInstanceId}) {
  return events
    .filter(event => event.activityInstanceId === activityInstanceId)
    .map(event => ({...event, label: getEventLabel(event)}));
}
