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

export default function InstanceFilters({openNewFilterModal, processDefinitionIsNotSelected}) {
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
      className="InstanceFilters Filter__dropdown"
    >
      <Dropdown.Option onClick={openNewFilterModal('instanceState')}>
        {t('common.filter.types.instanceState')}
      </Dropdown.Option>
      <Dropdown.Option onClick={openNewFilterModal('incidentInstances')}>
        {t('common.filter.types.incident')}
      </Dropdown.Option>
      <Dropdown.Submenu label={t('common.filter.types.date')}>
        <Dropdown.Option onClick={openNewFilterModal('instanceStartDate')}>
          {t('common.filter.types.instanceStartDate')}
        </Dropdown.Option>
        <Dropdown.Option onClick={openNewFilterModal('instanceEndDate')}>
          {t('common.filter.types.instanceEndDate')}
        </Dropdown.Option>
      </Dropdown.Submenu>
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
        onClick={openNewFilterModal('multipleVariable')}
      >
        {t('common.filter.types.variable')}
      </Dropdown.Option>
    </Dropdown>
  );
}
