/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Modal, DurationChart} from 'components';
import {t} from 'translation';

import {AnalysisProcessDefinitionParameters, SelectedNode, getOutlierSummary} from './service';
import VariablesTable from './VariablesTable';

import './OutlierDetailsModal.scss';

interface OutlierDetailsModalProps {
  selectedNode: SelectedNode;
  onClose: () => void;
  config: AnalysisProcessDefinitionParameters;
}

export default function OutlierDetailsModal({
  selectedNode,
  onClose,
  config,
}: OutlierDetailsModalProps) {
  const {name, higherOutlier, data, totalCount} = selectedNode;
  const {count, relation} = higherOutlier;

  return (
    <Modal open onClose={onClose} className="OutlierDetailsModal" size="lg">
      <Modal.Header>{t('analysis.task.detailsModal.title', {name})}</Modal.Header>
      <Modal.Content>
        <p className="description">
          {t('analysis.task.totalFlowNodeInstances', {count: totalCount})}
          <span>{getOutlierSummary(count, relation)}</span>
        </p>
        <h2>{t('analysis.task.detailsModal.durationChart')}</h2>
        <DurationChart
          data={data}
          colors={data.map(({outlier}) => (outlier ? '#1991c8' : '#eeeeee'))}
        />
        <h2>{t('analysis.task.detailsModal.variablesTable')}</h2>
        <VariablesTable config={config} selectedNode={selectedNode} />
      </Modal.Content>
    </Modal>
  );
}
