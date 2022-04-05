/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Up, Down} from './styled';

type Props = {
  sortOrder: 'asc' | 'desc';
};

const SortIcon: React.FC<Props> = ({sortOrder, ...rest}) => {
  return (
    <span {...rest} data-testid={`${sortOrder}-icon`}>
      {sortOrder === 'asc' ? <Up /> : <Down />}
    </span>
  );
};

export {SortIcon};
