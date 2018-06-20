import {format} from 'date-fns';

export function formatDate(dateString) {
  return dateString ? format(dateString, 'D MMM YYYY | HH:mm:ss') : '--';
}

export const getActiveIncident = incidents => {
  return (
    incidents &&
    incidents.length &&
    incidents.filter(({state}) => state === 'ACTIVE')[0]
  );
};

export function getInstanceState({state, incidents}) {
  if (state === 'COMPLETED' || state === 'CANCELED') {
    return state;
  }
  return getActiveIncident(incidents) ? 'INCIDENT' : 'ACTIVE';
}

export function getIncidentMessage({incidents}) {
  return (getActiveIncident(incidents) || {}).errorMessage;
}
