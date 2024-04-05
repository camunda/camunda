/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {format, parseISO} from 'date-fns';
import {logger} from './logger';

const formatDate = (dateString: string, showTime = true) => {
  try {
    return format(
      parseISO(dateString),
      showTime ? 'dd MMM yyyy - hh:mm a' : 'dd MMM yyyy',
    );
  } catch (error) {
    logger.error(error);
    return '';
  }
};

export {formatDate};
