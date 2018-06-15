import {formatDate} from 'modules/utils';

export function formatData(instance) {
  return {
    ...instance,
    startDate: formatDate(instance.startDate),
    endDate: formatDate(instance.endDate)
  };
}
