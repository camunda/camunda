/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DownloadButton} from 'components';
import {loadRawData, formatters} from 'services';
import {t} from 'translation';
import {useUser} from 'hooks';

import {AnalysisProcessDefinitionParameters} from './service';

interface InstancesButtonProps {
  id: string;
  name?: string;
  config: AnalysisProcessDefinitionParameters;
  value: number;
  totalCount: number;
}

export function InstancesButton({id, name, config, value, totalCount}: InstancesButtonProps) {
  const {filters, ...restConfig} = config;
  const {user} = useUser();

  return (
    <DownloadButton
      kind="tertiary"
      size="sm"
      retriever={() =>
        loadRawData({
          ...restConfig,
          filter: [
            {
              type: 'completedInstancesOnly',
              filterLevel: 'instance',
            },
            {
              type: 'flowNodeDuration',
              data: {[id]: {operator: '>=', value, unit: 'millis'}},
              filterLevel: 'instance',
            },
            ...filters,
          ],
          includedColumns: ['processInstanceId'],
        })
      }
      fileName={
        formatters.formatFileName(name || id) +
        '-' +
        t('analysis.task.tooltip.outlier.label-plural') +
        '.csv'
      }
      totalCount={totalCount}
      user={user}
    >
      {t('common.processInstanceIds')}
    </DownloadButton>
  );
}

export default InstancesButton;
