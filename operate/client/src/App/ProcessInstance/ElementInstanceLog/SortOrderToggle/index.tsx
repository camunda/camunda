/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Button} from '@carbon/react';
import {SortAscending, SortDescending} from '@carbon/react/icons';
import {instanceHistorySortOrderStore} from 'modules/stores/instanceHistorySortOrder';

const SortOrderToggle: React.FC = observer(() => {
  const {order, toggle} = instanceHistorySortOrderStore;
  const isLatestFirst = order === 'desc';

  return (
    <Button
      kind="ghost"
      size="sm"
      renderIcon={isLatestFirst ? SortDescending : SortAscending}
      onClick={toggle}
    >
      {isLatestFirst ? 'Latest first' : 'Oldest first'}
    </Button>
  );
});

export {SortOrderToggle};
