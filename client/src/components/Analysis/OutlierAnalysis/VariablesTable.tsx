/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';

import {Table, Icon, NoDataNotice, LoadingIndicator, DownloadButton} from 'components';
import {withUser, WithUserProps} from 'HOC';
import {t} from 'translation';

import {
  loadCommonOutliersVariables,
  getInstancesDownloadUrl,
  OutliersVariable,
  AnalysisProcessDefinitionParameters,
  SelectedNode,
} from './service';

import './VariablesTable.scss';

interface VariablesTableProps extends WithUserProps {
  selectedNode: SelectedNode;
  config: AnalysisProcessDefinitionParameters;
  totalCount: number;
}

export function VariablesTable({selectedNode, config, totalCount, user}: VariablesTableProps) {
  const [data, setData] = useState<OutliersVariable[]>();

  useEffect(() => {
    const loadData = async () => {
      const {id, higherOutlier} = selectedNode;
      const data = await loadCommonOutliersVariables({
        ...config,
        flowNodeId: id,
        higherOutlierBound: higherOutlier.boundValue,
      });
      setData(data);
    };
    loadData();
  }, [selectedNode, config]);

  const constructTableBody = (data: OutliersVariable[]): (string | JSX.Element)[][] => {
    const {id, higherOutlier} = selectedNode;
    return data.map((row) => [
      <div className="outliersCount">
        {row.instanceCount} {t(`common.instance.label${row.instanceCount !== 1 ? '-plural' : ''}`)}
        <DownloadButton
          href={getInstancesDownloadUrl({
            ...config,
            flowNodeId: id,
            higherOutlierBound: higherOutlier.boundValue,
            variableName: row.variableName,
            variableTerm: row.variableTerm,
          })}
          fileName={`${row.variableName}_Outliers.csv`}
          totalCount={totalCount}
          user={user}
        >
          <Icon type="save" />
          {t('common.instanceIds')}
        </DownloadButton>
      </div>,
      (+(row.outlierToAllInstancesRatio * 100).toFixed(2)).toString(),
      (+(row.outlierRatio * 100).toFixed(2)).toString(),
      row.variableName + '=' + row.variableTerm,
    ]);
  };

  let tableData;
  if (data?.length) {
    tableData = {
      head: [
        t('analysis.outlier.detailsModal.table.outliersNumber').toString(),
        t('analysis.outlier.detailsModal.table.ofTotalPercentage').toString(),
        t('analysis.outlier.detailsModal.table.ofOutliersPercentage').toString(),
        t('report.variables.default').toString(),
      ],
      body: constructTableBody(data),
    };
  } else {
    tableData = {
      head: [],
      body: [],
      noData: data ? (
        <NoDataNotice type="info">
          {t('analysis.outlier.detailsModal.table.emptyTableMessage')}
        </NoDataNotice>
      ) : (
        <LoadingIndicator />
      ),
    };
  }

  return (
    <div className="VariablesTable">
      <Table {...tableData} disablePagination />
    </div>
  );
}

export default withUser(VariablesTable);
