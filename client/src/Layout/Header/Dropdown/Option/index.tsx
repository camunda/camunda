/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {OptionButton, Li} from './styled';

interface Props {
  children: string;
  onClick: () => void;
  onKeyDown: (event: React.KeyboardEvent<Element>) => void;
}

const Option: React.FC<Props> = ({children, onClick, onKeyDown}) => {
  return (
    <Li onClick={onClick} onKeyDown={onKeyDown}>
      <OptionButton>{children}</OptionButton>
    </Li>
  );
};

export {Option};
