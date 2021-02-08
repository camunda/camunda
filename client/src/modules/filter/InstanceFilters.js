/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';

export default function InstanceFilters({
  filterByTypeOnly,
  openNewFilterModal,
  processDefinitionIsNotSelected,
}) {
  return (
    <Dropdown
      label={t('common.add')}
      id="ControlPanel__filters"
      className="InstanceFilters Filter__dropdown"
    >
      <Dropdown.Submenu label={t('common.filter.types.instanceState')}>
        <Dropdown.Option onClick={filterByTypeOnly('runningInstancesOnly')}>
          {t('common.filter.types.runningInstancesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('completedInstancesOnly')}>
          {t('common.filter.types.completedInstancesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('canceledInstancesOnly')}>
          {t('common.filter.types.canceledInstancesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('nonCanceledInstancesOnly')}>
          {t('common.filter.types.nonCanceledInstancesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('suspendedInstancesOnly')}>
          {t('common.filter.types.suspendedInstancesOnly')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('nonSuspendedInstancesOnly')}>
          {t('common.filter.types.nonSuspendedInstancesOnly')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Submenu label={t('common.filter.types.incident')}>
        <Dropdown.Option onClick={filterByTypeOnly('includesOpenIncident')}>
          {t('common.filter.types.includesOpenIncident')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('includesResolvedIncident')}>
          {t('common.filter.types.includesResolvedIncident')}
        </Dropdown.Option>
        <Dropdown.Option onClick={filterByTypeOnly('doesNotIncludeIncident')}>
          {t('common.filter.types.doesNotIncludeIncident')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Submenu label={t('common.filter.types.date')}>
        <Dropdown.Option onClick={openNewFilterModal('startDate')}>
          {t('common.filter.types.startDate')}
        </Dropdown.Option>
        <Dropdown.Option onClick={openNewFilterModal('endDate')}>
          {t('common.filter.types.endDate')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Submenu label={t('common.filter.types.duration')}>
        <Dropdown.Option onClick={openNewFilterModal('processInstanceDuration')}>
          {t('common.filter.types.instance')}
        </Dropdown.Option>
        <Dropdown.Option
          disabled={processDefinitionIsNotSelected}
          onClick={openNewFilterModal('flowNodeDuration')}
        >
          {t('common.filter.types.flowNode')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('executedFlowNodes')}
      >
        {t('common.filter.types.flowNode')}
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
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('variable')}
      >
        {t('common.filter.types.variable')}
      </Dropdown.Option>
    </Dropdown>
  );
}
