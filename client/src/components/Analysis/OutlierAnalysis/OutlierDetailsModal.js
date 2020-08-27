/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Tabs} from 'components';
import {t} from 'translation';

import DurationChart from './DurationChart';
import VariablesTable from './VariablesTable';
import InstancesButton from './InstancesButton';

import './OutlierDetailsModal.scss';

export default function OutlierDetailsModal({selectedNode, onClose, config}) {
  const {id, name, higherOutlier, data, totalCount} = selectedNode;

  return (
    <Modal open size="large" onClose={onClose} className="OutlierDetailsModal">
      <Modal.Header>{t('analysis.outlier.detailsModal.title', {name})}</Modal.Header>
      <Modal.Content>
        <Tabs>
          <Tabs.Tab title={t('analysis.outlier.detailsModal.durationChart')}>
            <p className="description">
              {t(
                `analysis.outlier.tooltipText.${higherOutlier.count === 1 ? 'singular' : 'plural'}`,
                {
                  count: higherOutlier.count,
                  percentage: Math.round(higherOutlier.relation * 100),
                }
              )}
              <InstancesButton
                id={id}
                name={name}
                value={higherOutlier.boundValue}
                config={config}
              />
            </p>
            <DurationChart data={data} />
          </Tabs.Tab>
          <Tabs.Tab title={t('analysis.outlier.detailsModal.variablesTable')}>
            <p className="description">
              {t('analysis.outlier.totalInstances')}: {totalCount}
            </p>
            <VariablesTable config={config} selectedNode={selectedNode} />
          </Tabs.Tab>
        </Tabs>
      </Modal.Content>
    </Modal>
  );
}
