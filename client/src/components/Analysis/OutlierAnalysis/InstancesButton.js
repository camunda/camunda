/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Icon} from 'components';
import {loadRawData, formatters} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';

export function InstancesButton({id, name, config, value, mightFail}) {
  return (
    <Button
      onClick={() => {
        mightFail(
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
          }),
          (data) => {
            const hiddenElement = document.createElement('a');
            hiddenElement.href = window.URL.createObjectURL(data);
            hiddenElement.download =
              formatters.formatFileName(name || id) +
              '-' +
              t('analysis.outlier.tooltip.outlier.label-plural') +
              '.csv';
            hiddenElement.click();
          },
          showError
        );
      }}
    >
      <Icon size="14" type="save" />
      {t('common.instanceIds')}
    </Button>
  );
}

export default withErrorHandling(InstancesButton);
