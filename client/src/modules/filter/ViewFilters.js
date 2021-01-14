/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';

export default function ViewFilters({
  filterByTypeOnly,
  openNewFilterModal,
  processDefinitionIsNotSelected,
}) {
  return (
    <Dropdown
      label={t('common.add')}
      id="ControlPanel__filters"
      className="ViewFilters Filter__dropdown"
    >
      <Dropdown.Submenu label={t('common.filter.types.flowNodeStatus')}>
        <Dropdown.Option onClick={filterByTypeOnly('runningFlowNodesOnly')}>
          {t('common.filter.types.runningFlowNodesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('completedFlowNodesOnly')}>
          {t('common.filter.types.completedFlowNodesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('canceledFlowNodesOnly')}>
          {t('common.filter.types.canceledFlowNodesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('completedOrCanceledFlowNodesOnly')}>
          {t('common.filter.types.completedOrCanceledFlowNodesOnly')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Submenu label={t('common.filter.types.incident')}>
        <Dropdown.Option onClick={filterByTypeOnly('includesOpenIncident')}>
          {t('common.filter.types.openIncident')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('includesResolvedIncident')}>
          {t('common.filter.types.resolvedIncident')}
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
