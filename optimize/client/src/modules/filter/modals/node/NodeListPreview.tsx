/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import {Tag} from '@carbon/react';

import {ModdleElement} from 'components';
import {t} from 'translation';

interface NodeListPreviewProps {
  nodes: ModdleElement[];
  operator?: string;
  type: string;
}

export default function NodeListPreview({nodes, operator, type}: NodeListPreviewProps) {
  const previewList: JSX.Element[] = [];
  const createOperator = (name: ReactNode) => {
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

  const parameterName = t(
    'common.filter.nodeModal.preview.' + (operator === 'not in' ? 'notExecutedFlowNodes' : type)
  ).toString();

  return (
    <>
      <Tag type="blue" className="parameterName">
        <span title={parameterName}>{parameterName}</span>
      </Tag>
      <ul className="previewList filterText">{previewList}</ul>
    </>
  );
}
