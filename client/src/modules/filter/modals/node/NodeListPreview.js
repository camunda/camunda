/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Tooltip} from 'components';
import React from 'react';

import {t} from 'translation';

export default function NodeListPreview({nodes, operator, type}) {
  const previewList = [];
  const createOperator = (name) => {
    return <span className="previewItemOperator"> {name} </span>;
  };

  nodes.forEach((selectedNode, idx) => {
    previewList.push(
      <li key={idx} className="previewItem">
        <span>
          {' '}
          {idx !== 0 &&
            createOperator(
              operator === 'not in'
                ? t('common.filter.list.operators.and')
                : t('common.filter.list.operators.or')
            )}
          <b>{selectedNode.name || selectedNode.id}</b>
        </span>
      </li>
    );
  });

  const parameterName = (
    <span className="parameterName">
      {t(
        'common.filter.nodeModal.preview.' + (operator === 'not in' ? 'notExecutedFlowNodes' : type)
      )}
    </span>
  );

  return (
    <>
      <Tooltip content={parameterName} overflowOnly>
        {parameterName}
      </Tooltip>
      <ul className="previewList filterText">{previewList}</ul>
    </>
  );
}
