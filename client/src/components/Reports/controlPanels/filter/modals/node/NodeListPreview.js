/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {t} from 'translation';

export default function NodeListPreview({nodes, operator}) {
  const previewList = [];
  const createOperator = name => {
    return <span className="previewItemOperator"> {name} </span>;
  };

  nodes.forEach((selectedNode, idx) => {
    previewList.push(
      <li key={idx} className="previewItem">
        <span>
          {' '}
          <span className="previewItemValue">{selectedNode.name || selectedNode.id}</span>{' '}
          {idx < nodes.length - 1 &&
            createOperator(
              operator === 'not in'
                ? t('common.filter.list.operators.nor')
                : t('common.filter.list.operators.or')
            )}
        </span>
      </li>
    );
  });
  return (
    <>
      <span className="parameterName">
        {!operator
          ? t('common.filter.list.executingFlowNode')
          : t('common.filter.list.executedFlowNode')}
      </span>
      {createOperator(
        operator === 'not in'
          ? nodes.length > 1
            ? t('common.filter.list.operators.neither')
            : t('common.filter.list.operators.not')
          : t('common.filter.list.operators.is')
      )}
      <ul className="previewList">{previewList}</ul>
    </>
  );
}
