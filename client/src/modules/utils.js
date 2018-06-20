import {format} from 'date-fns';

export function formatDate(dateString) {
  return dateString ? format(dateString, 'D MMM YYYY | HH:mm:ss') : '--';
}

export function getInstanceState({state, incidents}) {
  if (state === 'COMPLETED' || state === 'CANCELLED') {
    return state;
  }

  const activeIncident =
    incidents &&
    incidents.length &&
    incidents.filter(({state}) => state === 'ACTIVE')[0];

  return activeIncident ? 'INCIDENT' : 'ACTIVE';
}

export function getInstanceErrorMessage({incidents}) {
  const activeIncident =
    incidents &&
    incidents.length &&
    incidents.filter(({state}) => state === 'ACTIVE')[0];

  return (activeIncident || {}).errorMessage;
}
