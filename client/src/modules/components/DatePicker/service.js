import moment from 'moment';
const DATE_FORMAT = 'YYYY-MM-DD';

export function adjustRange({startLink, endLink}) {
  if (startLink.startOf('month').isSame(endLink.startOf('month'))) {
    endLink.add(1, 'months');
  }

  return {
    startLink: startLink,
    endLink: endLink,
    innerArrowsDisabled: shouldDisableInnerArrows(startLink, endLink)
  };
}

export function isDateValid(date) {
  const momentDate = moment(date, DATE_FORMAT);
  return momentDate.isValid() && momentDate.format(DATE_FORMAT) === date;
}

function shouldDisableInnerArrows(startLink, endLink) {
  return startLink
    .clone()
    .add(1, 'months')
    .startOf('month')
    .isSame(endLink.startOf('month'));
}
