/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DownloadButton} from 'components';
import {loadRawData, formatters} from 'services';
import {t} from 'translation';

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
    >
      {t('common.processInstanceIds')}
    </DownloadButton>
  );
}

export default InstancesButton;
