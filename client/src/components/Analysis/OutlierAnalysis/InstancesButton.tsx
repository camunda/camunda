/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {User} from 'HOC';
import {Icon, DownloadButton} from 'components';
import {loadRawData, formatters} from 'services';
import {t} from 'translation';

import {AnalysisProcessDefinitionParameters} from './service';

interface InstancesButtonProps {
  id: string;
  name: string;
  config: AnalysisProcessDefinitionParameters;
  user?: User;
  value: number;
  totalCount: number;
}

export function InstancesButton({id, name, config, value, totalCount, user}: InstancesButtonProps) {
  return (
    <DownloadButton
      retriever={() =>
        loadRawData({
          ...config,
          filter: [
            {
              type: 'flowNodeDuration',
              data: {[id]: {operator: '>', value, unit: 'millis'}},
              filterLevel: 'instance',
            },
          ],
          includedColumns: ['processInstanceId'],
        })
      }
      fileName={
        formatters.formatFileName(name || id) +
        '-' +
        t('analysis.outlier.tooltip.outlier.label-plural') +
        '.csv'
      }
      totalCount={totalCount}
      user={user}
    >
      <Icon size="14" type="save" />
      {t('common.instanceIds')}
    </DownloadButton>
  );
}

export default InstancesButton;
