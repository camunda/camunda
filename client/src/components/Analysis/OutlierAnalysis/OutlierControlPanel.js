/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {DefinitionSelection} from 'components';

import './OutlierControlPanel.scss';
import {t} from 'translation';

export default function OutlierControlPanel(props) {
  return (
    <div className="OutlierControlPanel">
      <ul className="list">
        <li className="item">
          <DefinitionSelection
            type="process"
            infoMessage={t('analysis.outlier.onlyCompletedHint')}
            definitionKey={props.processDefinitionKey}
            versions={props.processDefinitionVersions}
            tenants={props.tenantIds}
            xml={props.xml}
            onChange={({key, versions, tenantIds}) =>
              props.onChange({
                processDefinitionKey: key,
                processDefinitionVersions: versions,
                tenantIds,
              })
            }
          />
        </li>
        <li className="item">{t('analysis.outlier.info')}</li>
      </ul>
    </div>
  );
}
