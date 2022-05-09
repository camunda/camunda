/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {Dropdown} from 'components';
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
    <Dropdown
      label={t('common.add')}
      id="ControlPanel__filters"
      className="ViewFilters Filter__dropdown"
    >
      <Dropdown.Option onClick={openNewFilterModal('flowNodeStatus')}>
        {t('common.filter.types.flowNodeStatus')}
      </Dropdown.Option>
      <Dropdown.Submenu
        disabled={processDefinitionIsNotSelected}
        label={t('common.filter.types.flowNodeDate')}
      >
        <Dropdown.Option onClick={openNewFilterModal('flowNodeStartDate')}>
          {t('common.filter.types.instanceStartDate')}
        </Dropdown.Option>
        <Dropdown.Option onClick={openNewFilterModal('flowNodeEndDate')}>
          {t('common.filter.types.instanceEndDate')}
        </Dropdown.Option>
      </Dropdown.Submenu>
      <Dropdown.Option onClick={openNewFilterModal('incident')}>
        {t('common.filter.types.incident')}
      </Dropdown.Option>
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('flowNodeDuration')}
      >
        {t('common.filter.types.duration')}
      </Dropdown.Option>
      {optimizeProfile === 'platform' && (
        <>
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
        </>
      )}
      <Dropdown.Option
        disabled={processDefinitionIsNotSelected}
        onClick={openNewFilterModal('executedFlowNodes')}
      >
        {t('common.filter.types.flowNodeSelection')}
      </Dropdown.Option>
    </Dropdown>
  );
}
