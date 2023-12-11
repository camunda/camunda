/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import {MenuItem} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';
import classNames from 'classnames';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';

export default function ViewFilters({openNewFilterModal, processDefinitionIsNotSelected}) {
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
      className="ViewFilters Filter__dropdown"
    >
      <MenuItem
        label={t('common.filter.types.flowNodeStatus')}
        onClick={openNewFilterModal('flowNodeStatus')}
      />
      <MenuItem
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
      </MenuItem>
      <MenuItem
        label={t('common.filter.types.flowNodeDuration')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('flowNodeDuration')}
      />
      <MenuItem
        label={t('common.filter.types.incident')}
        onClick={openNewFilterModal('incident')}
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
        label={t('common.filter.types.flowNodeSelection')}
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('executedFlowNodes')}
      />
    </MenuDropdown>
  );
}
