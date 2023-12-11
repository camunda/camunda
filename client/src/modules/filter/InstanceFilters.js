/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import {MenuItem} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';
import classnames from 'classnames';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';

export default function InstanceFilters({openNewFilterModal, processDefinitionIsNotSelected}) {
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

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
      <MenuItem label={t('common.filter.types.date')}>
        <MenuItem
          label={t('common.filter.types.instanceStartDate')}
          onClick={openNewFilterModal('instanceStartDate')}
        />
        <MenuItem
          label={t('common.filter.types.instanceEndDate')}
          onClick={openNewFilterModal('instanceEndDate')}
        />
      </MenuItem>
      <MenuItem
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
      </MenuItem>
      <MenuItem label={t('common.filter.types.instanceDuration')}>
        <MenuItem
          label={t('common.filter.types.instance')}
          onClick={openNewFilterModal('processInstanceDuration')}
        />
        <MenuItem
          disabled={processDefinitionIsNotSelected}
          onClick={openNewFilterModal('flowNodeDuration')}
          label={t('common.filter.types.flowNode')}
        />
      </MenuItem>
      <MenuItem
        label={t('common.filter.types.flowNode')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('executedFlowNodes')}
      />
      <MenuItem
        label={t('common.filter.types.incident')}
        onClick={openNewFilterModal('incidentInstances')}
      />
      {optimizeProfile === 'platform' && (
        <>
          <MenuItem
            label={t('report.groupBy.userAssignee')}
            disabled={processDefinitionIsNotSelected}
            onClick={openNewFilterModal('assignee')}
          />
          <MenuItem
            label={t('report.groupBy.userGroup')}
            disabled={processDefinitionIsNotSelected}
            onClick={openNewFilterModal('candidateGroup')}
          />
        </>
      )}
      <MenuItem
        label={t('common.filter.types.variable')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('multipleVariable')}
      />
    </MenuDropdown>
  );
}
