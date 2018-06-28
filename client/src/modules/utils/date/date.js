import {format} from 'date-fns';

export function formatDate(dateString) {
  return dateString ? format(dateString, 'D MMM YYYY | HH:mm:ss') : '--';
}
