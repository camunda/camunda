/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {format, parseISO} from 'date-fns';

const formatDate = (dateString: string) => {
  try {
    return format(parseISO(dateString), 'yyyy-MM-dd HH:mm:ss');
  } catch (error) {
    console.error(error);
    return '';
  }
};

export {formatDate};
