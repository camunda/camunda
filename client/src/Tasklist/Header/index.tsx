/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import * as Styled from './styled';

const Header: React.FC = () => {
  return (
    <Styled.HeaderContent>
      <Styled.BrandInfo>
        <Styled.Brand to="/">
          <Styled.LogoIcon data-testid="logo" />
          <div>Zeebe Tasklist</div>
        </Styled.Brand>
      </Styled.BrandInfo>
      <Styled.UserControls />
    </Styled.HeaderContent>
  );
};

export {Header};
