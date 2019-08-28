/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import {Table, Button, Icon} from 'components';
import {loadCommonOutliersVariables, getInstancesDownloadUrl} from './service';
import {t} from 'translation';

export default class VariablesTable extends Component {
  state = {
    data: []
  };

  async componentDidMount() {
    const {id, higherOutlier} = this.props.selectedNode;
    const data = await loadCommonOutliersVariables({
      ...this.props.config,
      flowNodeId: id,
      higherOutlierBound: higherOutlier.boundValue
    });

    this.setState({data});
  }

  constructTableBody = data => {
    const {id, higherOutlier} = this.props.selectedNode;

    return data.map(row => [
      <>
        {row.instanceCount}{' '}
        {t(`analysis.outlier.tooltip.instance.label${row.instanceCount !== 1 ? '-plural' : ''}`)}
        <a
          href={getInstancesDownloadUrl({
            ...this.props.config,
            flowNodeId: id,
            higherOutlierBound: higherOutlier.boundValue,
            variableName: row.variableName,
            variableTerm: row.variableTerm,
            fileName: `${row.variableName}_Outliers.csv`
          })}
        >
          <Button>
            <Icon type="save" />
            Instance ID's CSV
          </Button>
        </a>
      </>,
      +(row.outlierToAllInstancesRatio * 100).toFixed(2),
      +(row.outlierRatio * 100).toFixed(2),
      row.variableName + '=' + row.variableTerm
    ]);
  };

  render() {
    const {data} = this.state;
    let tableData;
    if (data.length) {
      tableData = {
        head: [
          t('analysis.outlier.detailsModal.table.outliersNumber'),
          t('analysis.outlier.detailsModal.table.ofTotalPercentage'),
          t('analysis.outlier.detailsModal.table.ofOutliersPercentage'),
          t('report.variables.default')
        ],
        body: this.constructTableBody(data)
      };
    } else {
      tableData = {
        head: [t('report.table.noData.head')],
        body: [[t('analysis.outlier.detailsModal.table.emptyTableMessage')]]
      };
    }

    return (
      <div className="tableContainer">
        <Table {...tableData} foot={[]} disablePagination />
      </div>
    );
  }
}
