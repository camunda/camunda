/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Icon, Up, Down} from './styled';

type Props = {
  sortOrder: 'asc' | 'desc';
  disabled?: boolean;
};

const SortIcon: React.FC<Props> = ({sortOrder, disabled, ...rest}) => {
  return (
    <Icon {...rest} data-testid={`${sortOrder}-icon`} $disabled={disabled}>
      {sortOrder === 'asc' ? <Up /> : <Down />}
    </Icon>
  );
};

export {SortIcon};
