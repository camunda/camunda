/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

import {FlowNodeStateFilterType, IncidentFilterType, InstanceStateFilterType} from '../types';

export default function getMapping(type: string):
  | {
      modalTitle: string | JSX.Element[];
      pretext: string | JSX.Element[];
      mappings: {
        key: InstanceStateFilterType | FlowNodeStateFilterType | IncidentFilterType;
        label: string | JSX.Element[];
      }[];
    }
  | undefined {
  switch (type) {
    case 'instanceState':
      return {
        modalTitle: t('common.filter.types.instanceState'),
        pretext: t('common.filter.state.modalPretext.instanceState'),
        mappings: [
          {
            key: 'runningInstancesOnly',
            label: t('common.filter.state.modalLabels.runningInstancesOnly'),
          },
          {
            key: 'completedInstancesOnly',
            label: t('common.filter.state.modalLabels.completedInstancesOnly'),
          },
          {
            key: 'canceledInstancesOnly',
            label: t('common.filter.state.modalLabels.canceledInstancesOnly'),
          },
          {
            key: 'nonCanceledInstancesOnly',
            label: t('common.filter.state.modalLabels.nonCanceledInstancesOnly'),
          },
          {
            key: 'suspendedInstancesOnly',
            label: t('common.filter.state.modalLabels.suspendedInstancesOnly'),
          },
          {
            key: 'nonSuspendedInstancesOnly',
            label: t('common.filter.state.modalLabels.nonSuspendedInstancesOnly'),
          },
        ],
      };
    case 'incidentInstances':
      return {
        modalTitle: t('common.filter.types.incident'),
        pretext: t('common.filter.state.modalPretext.incidentInstances'),
        mappings: [
          {
            key: 'includesOpenIncident',
            label: t('common.filter.state.modalLabels.includesOpenIncident'),
          },
          {
            key: 'includesResolvedIncident',
            label: t('common.filter.state.modalLabels.includesResolvedIncident'),
          },
          {
            key: 'includesClosedIncident',
            label: t('common.filter.state.modalLabels.includesClosedIncident'),
          },
          {
            key: 'doesNotIncludeIncident',
            label: t('common.filter.state.modalLabels.doesNotIncludeIncident'),
          },
        ],
      };
    case 'flowNodeStatus':
      return {
        modalTitle: t('common.filter.types.flowNodeStatus'),
        pretext: t('common.filter.state.modalPretext.flowNodeStatus'),
        mappings: [
          {
            key: 'runningFlowNodesOnly',
            label: t('common.filter.state.modalLabels.runningFlowNodesOnly'),
          },
          {
            key: 'completedFlowNodesOnly',
            label: t('common.filter.state.modalLabels.completedFlowNodesOnly'),
          },
          {
            key: 'canceledFlowNodesOnly',
            label: t('common.filter.state.modalLabels.canceledFlowNodesOnly'),
          },
          {
            key: 'completedOrCanceledFlowNodesOnly',
            label: t('common.filter.state.modalLabels.completedOrCanceledFlowNodesOnly'),
          },
        ],
      };
    case 'incident':
      return {
        modalTitle: t('common.filter.types.incident'),
        pretext: t('common.filter.state.modalPretext.incident'),
        mappings: [
          {
            key: 'includesOpenIncident',
            label: t('common.filter.state.modalLabels.includesOpenIncident'),
          },
          {
            key: 'includesResolvedIncident',
            label: t('common.filter.state.modalLabels.includesResolvedIncident'),
          },
        ],
      };
    default:
      return;
  }
}
