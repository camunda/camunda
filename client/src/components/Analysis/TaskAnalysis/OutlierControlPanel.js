/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';
import update from 'immutability-helper';
import {DefinitionSelection} from 'components';

import {t} from 'translation';
import {Filter} from 'filter';
import {loadVariables} from 'services';

import './OutlierControlPanel.scss';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

export default function OutlierControlPanel({
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  xml,
  onChange,
  filters,
}) {
  const [variables, setVariables] = useState([]);
  const {mightFail} = useErrorHandling();

  const definitions = [];
  if (processDefinitionKey) {
    definitions.push({
      identifier: 'definition',
      key: processDefinitionKey,
      versions: processDefinitionVersions,
      tenantIds: tenantIds,
      name: processDefinitionKey,
      displayName: processDefinitionKey,
    });
  }

  const fetchVariables = useCallback(
    function () {
      if (processDefinitionKey && processDefinitionVersions) {
        mightFail(
          loadVariables([{processDefinitionKey, processDefinitionVersions, tenantIds}]),
          setVariables,
          showError
        );
      }
    },
    [processDefinitionKey, processDefinitionVersions, tenantIds, mightFail]
  );

  useEffect(() => {
    fetchVariables();
  }, [fetchVariables]);

  return (
    <div className="OutlierControlPanel">
      <ul className="list">
        <li className="item">
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
        </li>
        <li className="item">{t('analysis.task.info')}</li>
        <li className="item itemFilter">
          <Filter
            data={filters}
            onChange={({filter: updatedFilter}) =>
              onChange({filters: update(filters, updatedFilter)})
            }
            definitions={definitions}
            filterLevel="instance"
            variables={variables}
          />
        </li>
      </ul>
    </div>
  );
}
