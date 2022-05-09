/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Table, Icon, NoDataNotice, LoadingIndicator, DownloadButton} from 'components';
import {loadCommonOutliersVariables, getInstancesDownloadUrl} from './service';
import {t} from 'translation';
import './VariablesTable.scss';

export default class VariablesTable extends React.Component {
  state = {
    data: null,
  };

  async componentDidMount() {
    const {id, higherOutlier} = this.props.selectedNode;
    const data = await loadCommonOutliersVariables({
      ...this.props.config,
      flowNodeId: id,
      higherOutlierBound: higherOutlier.boundValue,
    });

    this.setState({data});
  }

  constructTableBody = (data) => {
    const {id, higherOutlier} = this.props.selectedNode;

    return data.map((row) => [
      <div className="outliersCount">
        {row.instanceCount} {t(`common.instance.label${row.instanceCount !== 1 ? '-plural' : ''}`)}
        <DownloadButton
          href={getInstancesDownloadUrl({
            ...this.props.config,
            flowNodeId: id,
            higherOutlierBound: higherOutlier.boundValue,
            variableName: row.variableName,
            variableTerm: row.variableTerm,
          })}
          fileName={`${row.variableName}_Outliers.csv`}
          totalCount={this.props.totalCount}
        >
          <Icon type="save" />
          {t('common.instanceIds')}
        </DownloadButton>
      </div>,
      +(row.outlierToAllInstancesRatio * 100).toFixed(2),
      +(row.outlierRatio * 100).toFixed(2),
      row.variableName + '=' + row.variableTerm,
    ]);
  };

  render() {
    const {data} = this.state;
    let tableData;
    if (data?.length) {
      tableData = {
        head: [
          t('analysis.outlier.detailsModal.table.outliersNumber'),
          t('analysis.outlier.detailsModal.table.ofTotalPercentage'),
          t('analysis.outlier.detailsModal.table.ofOutliersPercentage'),
          t('report.variables.default'),
        ],
        body: this.constructTableBody(data),
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
        <Table {...tableData} foot={[]} disablePagination />
      </div>
    );
  }
}
