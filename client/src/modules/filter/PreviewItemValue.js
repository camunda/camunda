/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Tooltip} from 'components';

import './PreviewItemValue.scss';

export default function PreviewItemValue({children}) {
  return (
    <Tooltip content={children} overflowOnly>
      <span className="PreviewItemValue">{children}</span>
    </Tooltip>
  );
}
