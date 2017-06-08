import {Statistics as InnerStatistics} from './statistics';
import {decorateWithSelector, withSelector} from 'view-utils';

export const Statistics = withSelector(
  decorateWithSelector(InnerStatistics, getStatisticsState)
);

function getStatisticsState({analytics: {selection, statistics}}) {
  return {
    ...statistics,
    selection
  };
}
