/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuItem} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';
import classnames from 'classnames';

import {useUiConfig} from 'hooks';
import {Select} from 'components';
import {t} from 'translation';

export default function InstanceFilters({openNewFilterModal, processDefinitionIsNotSelected}) {
  const {userTaskAssigneeAnalyticsEnabled} = useUiConfig();

  return (
    <MenuDropdown
      size="sm"
      kind="ghost"
      label={t('common.add')}
      id="ControlPanel__filters"
      className="InstanceFilters Filter__dropdown"
    >
      <MenuItem
        label={t('common.filter.types.instanceState')}
        onClick={openNewFilterModal('instanceState')}
      />
      <Select.Submenu label={t('common.filter.types.date')}>
        <MenuItem
          label={t('common.filter.types.instanceStartDate')}
          onClick={openNewFilterModal('instanceStartDate')}
        />
        <MenuItem
          label={t('common.filter.types.instanceEndDate')}
          onClick={openNewFilterModal('instanceEndDate')}
        />
      </Select.Submenu>
      <Select.Submenu
        className={classnames({'cds--menu-item--disabled': processDefinitionIsNotSelected})}
        label={t('common.filter.types.flowNodeDate')}
      >
        <MenuItem
          disabled={processDefinitionIsNotSelected}
          label={t('common.filter.types.instanceStartDate')}
          onClick={openNewFilterModal('flowNodeStartDate')}
        />
        <MenuItem
          disabled={processDefinitionIsNotSelected}
          label={t('common.filter.types.instanceEndDate')}
          onClick={openNewFilterModal('flowNodeEndDate')}
        />
      </Select.Submenu>
      <Select.Submenu label={t('common.filter.types.instanceDuration')}>
        <MenuItem
          label={t('common.filter.types.instance')}
          onClick={openNewFilterModal('processInstanceDuration')}
        />
        <MenuItem
          disabled={processDefinitionIsNotSelected}
          onClick={openNewFilterModal('flowNodeDuration')}
          label={t('common.filter.types.flowNode')}
        />
      </Select.Submenu>
      <MenuItem
        label={t('common.filter.types.flowNode')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('executedFlowNodes')}
      />
      <MenuItem
        label={t('common.filter.types.incident')}
        onClick={openNewFilterModal('incidentInstances')}
      />
      {userTaskAssigneeAnalyticsEnabled && (
        <MenuItem
          label={t('report.groupBy.userAssignee')}
          disabled={processDefinitionIsNotSelected}
          onClick={openNewFilterModal('assignee')}
        />
      )}
      <MenuItem
        label={t('common.filter.types.variable')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('multipleVariable')}
      />
    </MenuDropdown>
  );
}
