/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './NoDataNotice.scss';

export default function NoDataNotice({children}) {
  return (
    <div className="NoDataNotice">
      <h1>No data to display</h1>
      <p>{children}</p>
    </div>
  );
}
