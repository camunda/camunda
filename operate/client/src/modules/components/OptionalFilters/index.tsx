/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ReactElement} from 'react';
import {OverflowMenuItem} from '@carbon/react';
import {Filter} from '@carbon/react/icons';
import {ButtonStack, OverflowMenu, Container} from './styled';

type Props<T> = {
  visibleFilters: T[];
  optionalFilters: {id: T; label: string}[];
  onFilterSelect: (filter: T) => void;
};

const OptionalFiltersMenu = <T extends string>({
  visibleFilters,
  optionalFilters,
  onFilterSelect,
}: Props<T>): ReactElement | null => {
  const unselectedOptionalFilters = optionalFilters.filter(
    (filter) => !visibleFilters.includes(filter.id),
  );

  return unselectedOptionalFilters.length > 0 ? (
    <Container>
      <OverflowMenu
        direction="top"
        aria-label="optional-filters-menu"
        iconDescription="More Filters"
        flipped
        renderIcon={() => (
          <ButtonStack orientation="horizontal" gap={3}>
            <span>More Filters</span>
            <Filter />
          </ButtonStack>
        )}
      >
        {unselectedOptionalFilters.map((filter) => (
          <OverflowMenuItem
            key={filter.id}
            itemText={filter.label}
            onClick={() => onFilterSelect(filter.id)}
            data-testid={`optional-filter-menuitem-${filter.id}`}
          />
        ))}
      </OverflowMenu>
    </Container>
  ) : null;
};

export {OptionalFiltersMenu};
