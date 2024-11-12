/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuItem} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';
import classNames from 'classnames';

import {Select} from 'components';
import {t} from 'translation';
import {useUiConfig} from 'hooks';

export default function ViewFilters({openNewFilterModal, processDefinitionIsNotSelected}) {
  const {userTaskAssigneeAnalyticsEnabled} = useUiConfig();

  return (
    <MenuDropdown
      size="sm"
      kind="ghost"
      label={t('common.add')}
      id="ControlPanel__filters"
      className="ViewFilters Filter__dropdown"
    >
      <MenuItem
        label={t('common.filter.types.flowNodeStatus')}
        onClick={openNewFilterModal('flowNodeStatus')}
      />
      <Select.Submenu
        className={classNames({'cds--menu-item--disabled': processDefinitionIsNotSelected})}
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
      <MenuItem
        label={t('common.filter.types.flowNodeDuration')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('flowNodeDuration')}
      />
      <MenuItem
        label={t('common.filter.types.incident')}
        onClick={openNewFilterModal('incident')}
      />
      {userTaskAssigneeAnalyticsEnabled && (
        <MenuItem
          label={t('report.groupBy.userAssignee')}
          disabled={processDefinitionIsNotSelected}
          onClick={openNewFilterModal('assignee')}
        />
      )}
      <MenuItem
        label={t('common.filter.types.flowNodeSelection')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('executedFlowNodes')}
      />
    </MenuDropdown>
  );
}
