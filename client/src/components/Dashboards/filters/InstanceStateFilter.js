/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import {Button, Form, FormGroup, Stack, Toggle} from '@carbon/react';

import {Popover} from 'components';
import {incompatibleFilters} from 'services';
import {t} from 'translation';

import './InstanceStateFilter.scss';

const types = [
  'runningInstancesOnly',
  'completedInstancesOnly',
  'canceledInstancesOnly',
  'nonCanceledInstancesOnly',
  'suspendedInstancesOnly',
  'nonSuspendedInstancesOnly',
];

export default function InstanceStateFilter({filter = [], setFilter, children}) {
  const stateFilter = filter.filter(({type}) => types.includes(type));

  function hasFilter(type) {
    return filter.some((filter) => filter.type === type);
  }

  function addFilter(type) {
    setFilter([...filter, {type, data: null, filterLevel: 'instance'}]);
  }

  function removeFilter(type) {
    setFilter(filter.filter((filter) => filter.type !== type));
  }

  function isAllowed(type) {
    return !incompatibleFilters([...stateFilter, {type}]);
  }

  const active = types.some(hasFilter);

  return (
    <div className="InstanceStateFilter">
      <div className="title">
        {t('dashboard.filter.types.state')}
        {children}
      </div>
      <Popover
        isTabTip
        trigger={
          <Popover.Button size="sm" kind="tertiary">
            <span className={classnames('indicator', {active})} />
            {stateFilter.map(({type}) => t('dashboard.filter.types.' + type)).join(', ') ||
              t('common.off')}
          </Popover.Button>
        }
      >
        <Form>
          <FormGroup legendText={t('dashboard.filter.types.state')}>
            <Stack gap={4}>
              {types.map((type) => (
                <Toggle
                  key={type}
                  id={type}
                  size="sm"
                  labelA={t('dashboard.filter.types.' + type)}
                  labelB={t('dashboard.filter.types.' + type)}
                  toggled={hasFilter(type)}
                  disabled={!isAllowed(type)}
                  onToggle={(checked) => {
                    if (checked) {
                      addFilter(type);
                    } else {
                      removeFilter(type);
                    }
                  }}
                />
              ))}
            </Stack>
          </FormGroup>
          <hr />
          <Button
            size="sm"
            kind="ghost"
            className="reset-button"
            disabled={!active}
            onClick={() => setFilter(filter.filter(({type}) => !types.includes(type)))}
          >
            {t('common.off')}
          </Button>
        </Form>
      </Popover>
    </div>
  );
}
