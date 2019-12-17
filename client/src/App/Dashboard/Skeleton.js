/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import MultiRow from 'modules/components/MultiRow';

export default props => {
  return (
    <Styled.MultiRowContainer>
      <MultiRow Component={Styled.Block} {...props} />
    </Styled.MultiRowContainer>
  );
};
