/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './SetupNotice.scss';

export default function SetupNotice({children}) {
  return (
    <div className="SetupNotice">
      <h1>Set-up your Report</h1>
      {children}
    </div>
  );
}
