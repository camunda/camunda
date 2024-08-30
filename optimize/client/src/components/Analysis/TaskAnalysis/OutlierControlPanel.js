/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';
import {Information} from '@carbon/icons-react';

import {DefinitionSelection} from 'components';
import {t} from 'translation';

import {ControlPanel} from '../ControlPanel';

import './OutlierControlPanel.scss';

export default function OutlierControlPanel({
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  xml,
  onChange,
  filters,
}) {
  return (
    <ControlPanel
      processDefinitionKey={processDefinitionKey}
      processDefinitionVersions={processDefinitionVersions}
      tenantIds={tenantIds}
      onChange={onChange}
      filters={filters}
    >
      <DefinitionSelection
        type="process"
        infoMessage={t('analysis.task.onlyCompletedHint')}
        definitionKey={processDefinitionKey}
        versions={processDefinitionVersions}
        tenants={tenantIds}
        xml={xml}
        onChange={({key, versions, tenantIds}) =>
          onChange({
            processDefinitionKey: key,
            processDefinitionVersions: versions,
            tenantIds,
          })
        }
      />
      {t('analysis.task.info')}
      <Tooltip
        description={t('analysis.task.tooltip.zScore')}
        align="bottom"
        className="zScoreTooltip"
      >
        <Information />
      </Tooltip>
    </ControlPanel>
  );
}
