/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Row, Delete} from './styled';
import {
  processInstancesVisibleFiltersStore,
  OptionalFilter as OptionalFilterType,
} from 'modules/stores/processInstancesVisibleFilters';
import {observer} from 'mobx-react';
import {useForm} from 'react-final-form';
import {ProcessInstanceFilterField} from 'modules/utils/filter';

type Props = {
  name: OptionalFilterType;
  children: React.ReactNode;
  filterList: Array<ProcessInstanceFilterField>;
};

const OptionalFilter: React.FC<Props> = observer(
  ({name, children, filterList}) => {
    const {visibleFilters} = processInstancesVisibleFiltersStore.state;
    const form = useForm();

    return (
      <Row order={visibleFilters.indexOf(name)}>
        <Delete
          icon="delete"
          data-testid={`delete-${name}`}
          onClick={() => {
            processInstancesVisibleFiltersStore.hideFilter(name);

            filterList.forEach((filter) => {
              form.change(filter, undefined);
            });

            form.submit();
          }}
        />
        {children}
      </Row>
    );
  }
);

export {OptionalFilter};
