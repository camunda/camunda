/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {EmptyMessage} from '../EmptyMessage';

type Props = {
  message?: string;
  additionalInfo?: string;
};

const DEFAULT_ERROR = {
  message: 'Data could not be fetched',
  additionalInfo: 'Refresh the page to try again',
};

const ErrorMessage: React.FC<Props> = (props) => {
  return <EmptyMessage {...DEFAULT_ERROR} {...props} />;
};

export {ErrorMessage};
