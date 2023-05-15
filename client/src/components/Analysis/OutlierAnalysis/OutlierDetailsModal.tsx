/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CarbonModal as Modal, Tabs, DurationChart} from 'components';
import {t} from 'translation';
import {WithUserProps, withUser} from 'HOC';

import {AnalysisProcessDefinitionParameters, SelectedNode} from './service';
import VariablesTable from './VariablesTable';
import InstancesButton from './InstancesButton';

import './OutlierDetailsModal.scss';

interface OutlierDetailsModalProps extends WithUserProps {
  selectedNode: SelectedNode;
  onClose: () => void;
  config: AnalysisProcessDefinitionParameters;
}

export function OutlierDetailsModal({
  selectedNode,
  onClose,
  config,
  user,
}: OutlierDetailsModalProps) {
  const {id, name, higherOutlier, data, totalCount} = selectedNode;

  return (
    <Modal open onClose={onClose} className="OutlierDetailsModal" size="lg">
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
                totalCount={totalCount}
                user={user}
              />
            </p>
            <DurationChart
              data={data}
              colors={data.map(({outlier}) => (outlier ? '#1991c8' : '#eeeeee'))}
            />
          </Tabs.Tab>
          <Tabs.Tab title={t('analysis.outlier.detailsModal.variablesTable')}>
            <p className="description">
              {t('analysis.outlier.totalInstances')}: {totalCount}
            </p>
            <VariablesTable config={config} selectedNode={selectedNode} totalCount={totalCount} />
          </Tabs.Tab>
        </Tabs>
      </Modal.Content>
    </Modal>
  );
}

export default withUser(OutlierDetailsModal);
