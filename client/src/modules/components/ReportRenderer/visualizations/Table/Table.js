/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import processRawData from './processRawData';

import {Table as TableRenderer, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';

import ColumnRearrangement from './ColumnRearrangement';
import processCombinedData from './processCombinedData';
import processDefaultData from './processDefaultData';

import {getCamundaEndpoints} from './service';

export default withErrorHandling(
  class Table extends React.Component {
    state = {
      camundaEndpoints: null,
      needEndpoint: false
    };

    static getDerivedStateFromProps({report: {result, combined, data}}) {
      if (result && !combined && data.view.property === 'rawData') {
        return {needEndpoint: true};
      }
      return null;
    }

    componentDidMount() {
      if (this.state.needEndpoint) {
        this.props.mightFail(getCamundaEndpoints(), camundaEndpoints =>
          this.setState({camundaEndpoints})
        );
      }
    }

    render() {
      const {report, disableReportScrolling, updateReport} = this.props;

      if (this.state.needEndpoint && this.state.camundaEndpoints === null) {
        return <LoadingIndicator />;
      }

      return (
        <ColumnRearrangement report={report} updateReport={updateReport}>
          <TableRenderer disableReportScrolling={disableReportScrolling} {...this.formatData()} />
        </ColumnRearrangement>
      );
    }

    updateSorting = (by, order) => {
      this.props.updateReport({configuration: {sorting: {$set: {by, order}}}}, true);
    };

    formatData = () => {
      const {
        report: {reportType, combined, data, result},
        updateReport
      } = this.props;
      const {configuration} = data;

      // Combined Report
      if (combined) {
        return processCombinedData(this.props);
      }

      let tableData;
      // raw data
      if (data.view.property === 'rawData') {
        tableData = processRawData[reportType](this.props, this.state.camundaEndpoints);
      } else {
        // Normal single Report
        tableData = processDefaultData(this.props);
      }

      return {
        ...tableData,
        resultType: result.type,
        sortByLabel: data.groupBy.type === 'flowNodes',
        updateSorting: updateReport && this.updateSorting,
        sorting: configuration && configuration.sorting
      };
    };
  }
);
