/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal, DurationChart} from 'components';
import {t} from 'translation';
import {Stack} from '@carbon/react';

import {
  AnalysisProcessDefinitionParameters,
  OutlierNode,
  shouldUseLogharitmicScale,
  getOutlierSummary,
} from './service';
import VariablesTable from './VariablesTable';

import './OutlierDetailsModal.scss';

interface OutlierDetailsModalProps {
  selectedOutlierNode: OutlierNode;
  onClose: () => void;
  config: AnalysisProcessDefinitionParameters;
}

const MIN_OUTLIER_TO_MAX_NONOUTLIER_RATIO = 100;

export default function OutlierDetailsModal({
  selectedOutlierNode,
  onClose,
  config,
}: OutlierDetailsModalProps) {
  const {name, higherOutlier, data, totalCount} = selectedOutlierNode;
  const {count, relation} = higherOutlier;

  return (
    <Modal open onClose={onClose} className="OutlierDetailsModal" size="lg">
      <Modal.Header title={t('analysis.task.detailsModal.title', {name})} />
      <Modal.Content>
        <Stack gap={4}>
          <p className="description">
            {t('analysis.task.totalFlowNodeInstances', {count: totalCount})}
            <span>{getOutlierSummary(count, relation)}</span>
          </p>
          <h4 className="subTitle">{t('analysis.task.detailsModal.durationChart')}</h4>
        </Stack>
        <DurationChart
          data={data}
          colors={data.map(({outlier}) => (outlier ? '#1991c8' : '#eeeeee'))}
          isLogharitmic={shouldUseLogharitmicScale(data, MIN_OUTLIER_TO_MAX_NONOUTLIER_RATIO)}
        />
        <h4 className="subTitle">{t('analysis.task.detailsModal.variablesTable')}</h4>
        <VariablesTable config={config} selectedOutlierNode={selectedOutlierNode} />
      </Modal.Content>
    </Modal>
  );
}
