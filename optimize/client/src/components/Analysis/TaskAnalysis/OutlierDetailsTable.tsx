/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Table, TableBody} from 'components';
import {t} from 'translation';
import {Button} from '@carbon/react';

import {AnalysisProcessDefinitionParameters, getOutlierSummary} from './service';
import InstancesButton from './InstanceButton';

import './OutlierDetailsTable.scss';

type TaskData = {
  totalCount: number;
  higherOutlier?: {count: number; relation: number; boundValue: number};
};

interface OutlierDetailsTableProps {
  loading?: boolean;
  nodeOutliers: Record<string, TaskData | undefined>;
  flowNodeNames: Record<string, string>;
  onDetailsClick: (taskId: string, taskData: TaskData) => string;
  config: AnalysisProcessDefinitionParameters;
}

export default function OutlierDetailsTable({
  loading,
  nodeOutliers,
  flowNodeNames,
  onDetailsClick,
  config,
}: OutlierDetailsTableProps) {
  function parseTableBody(): TableBody[] {
    if (!nodeOutliers) {
      return [];
    }

    return Object.entries(nodeOutliers).reduce<TableBody[]>(
      (tableRows, [nodeOutlierId, nodeOutlierData]) => {
        if (!nodeOutlierData || !nodeOutlierData.higherOutlier) {
          return tableRows;
        }

        const {
          higherOutlier: {count, relation, boundValue},
          totalCount,
        } = nodeOutlierData;

        tableRows.push([
          flowNodeNames[nodeOutlierId] || nodeOutlierId,
          totalCount.toString(),
          getOutlierSummary(count, relation),
          <Button
            kind="tertiary"
            size="sm"
            onClick={() => onDetailsClick(nodeOutlierId, nodeOutlierData)}
          >
            {t('common.viewDetails')}
          </Button>,
          <InstancesButton
            id={nodeOutlierId}
            name={flowNodeNames[nodeOutlierId]}
            value={boundValue}
            config={config}
            totalCount={totalCount}
          />,
        ]);

        return tableRows;
      },
      []
    );
  }

  return (
    <Table
      className="OutlierDetailsTable"
      head={[
        t('analysis.task.table.flowNodeName').toString(),
        t('analysis.task.totalInstances').toString(),
        t('analysis.task.table.outliers').toString(),
        t('common.details').toString(),
        t('common.download').toString(),
      ]}
      body={parseTableBody()}
      loading={loading}
      disablePagination
    />
  );
}
