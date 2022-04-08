/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import './LoadingIndicator.scss';
import classnames from 'classnames';

export default function LoadingIndicator({small, className, ...props}) {
  return <div {...props} className={classnames('LoadingIndicator', className, {small})} />;
}
