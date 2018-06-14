import moment from 'moment';

export function formatData(instance) {
  return {
    ...instance,
    startDate: formatDate(instance.startDate),
    endDate: formatDate(instance.endDate)
  };
}

function formatDate(dateString) {
  if (dateString) {
    return moment(dateString).format('D MMM Y | HH:mm:ss');
  }
}
