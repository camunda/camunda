/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import moment from 'moment';
export const DATE_FORMAT = 'YYYY-MM-DD';

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
