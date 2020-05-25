/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

interface Props {
  children: string;
  onClick: () => void;
  onKeyDown: (event: React.KeyboardEvent<Element>) => void;
}

const Option: React.FC<Props> = ({children, onClick, onKeyDown}) => {
  return (
    <Styled.Li onClick={onClick} onKeyDown={onKeyDown}>
      <Styled.OptionButton>{children}</Styled.OptionButton>
    </Styled.Li>
  );
};

export {Option};
