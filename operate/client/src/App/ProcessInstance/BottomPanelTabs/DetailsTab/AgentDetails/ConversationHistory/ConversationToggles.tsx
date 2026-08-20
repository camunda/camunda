/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.10';
import {Button, ButtonSet} from '@carbon/react';
import {
  Filter,
  FilterRemove,
  SortAscending,
  SortDescending,
} from '@carbon/react/icons';

type ConversationTogglesProps = {
  sortOrder: QuerySortOrder;
  canBeScoped: boolean;
  isScoped: boolean;
  onToggleSortOrder: () => void;
  onToggleScope: () => void;
};

const ConversationToggles: React.FC<ConversationTogglesProps> = (props) => (
  <ButtonSet>
    <Button
      kind="ghost"
      size="xs"
      renderIcon={props.sortOrder === 'desc' ? SortDescending : SortAscending}
      onClick={props.onToggleSortOrder}
    >
      {props.sortOrder === 'desc' ? 'Most recent first' : 'Oldest first'}
    </Button>
    {props.canBeScoped && (
      <Button
        kind="ghost"
        size="xs"
        renderIcon={props.isScoped ? Filter : FilterRemove}
        onClick={props.onToggleScope}
      >
        {props.isScoped ? 'Scoped conversation' : 'Whole conversation'}
      </Button>
    )}
  </ButtonSet>
);

export {ConversationToggles};
