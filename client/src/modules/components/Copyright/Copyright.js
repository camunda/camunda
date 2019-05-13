/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

function Copyright(props) {
  return (
    <Styled.Copyright {...props}>
      &copy; Camunda Services GmbH {new Date().getFullYear()}. All rights
      reserved.
    </Styled.Copyright>
  );
}

export default Copyright;
