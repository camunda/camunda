/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';

export default function ViewFilters({
  filterByInstancesOnly,
  openNewFilterModal,
  processDefinitionIsNotSelected,
}) {
  return (
    <Dropdown
      label={t('common.add')}
      id="ControlPanel__filters"
      className="ViewFilters Filter__dropdown"
    >
      <Dropdown.Submenu label={t('common.filter.types.incident')}>
        <Dropdown.Option onClick={filterByInstancesOnly('includesOpenIncident')}>
          {t('common.filter.types.includesOpenIncident')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByInstancesOnly('includesResolvedIncident')}>
          {t('common.filter.types.includesResolvedIncident')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('flowNodeDuration')}
      >
        {t('common.filter.types.duration')}
      </Dropdown.Option>
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('assignee')}
      >
        {t('report.groupBy.userAssignee')}
      </Dropdown.Option>
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('candidateGroup')}
      >
        {t('report.groupBy.userGroup')}
      </Dropdown.Option>
    </Dropdown>
  );
}
