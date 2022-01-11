/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Row, Delete} from './styled';
import {
  visibleFiltersStore,
  OptionalFilter as OptionalFilterType,
} from 'modules/stores/visibleFilters';
import {observer} from 'mobx-react';
import {useFormState} from 'react-final-form';
import {FilterFieldsType} from 'modules/utils/filter';
import {useFilters} from '../useFilters';

type Props = {
  name: OptionalFilterType;
  children: React.ReactNode;
  filterList: Array<FilterFieldsType>;
};

const OptionalFilter: React.FC<Props> = observer(
  ({name, children, filterList}) => {
    const {visibleFilters} = visibleFiltersStore.state;
    const formState = useFormState();
    const filters = useFilters();

    return (
      <Row order={visibleFilters.indexOf(name)}>
        <Delete
          icon="delete"
          data-testid={`delete-${name}`}
          onClick={() => {
            let updatedFilters = Object.assign({}, formState.values);

            filterList.forEach((filter) => {
              if (updatedFilters.hasOwnProperty(filter)) {
                delete updatedFilters[filter];
              }
            });

            filters.setFiltersToURL(updatedFilters);
            visibleFiltersStore.hideFilter(name);
          }}
        />
        {children}
      </Row>
    );
  }
);

export {OptionalFilter};
