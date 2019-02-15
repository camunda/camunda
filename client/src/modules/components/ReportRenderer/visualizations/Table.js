import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer, LoadingIndicator} from 'components';
import {processRawData, reportConfig, formatters} from 'services';
import {withErrorHandling} from 'HOC';

import {
  getCamundaEndpoints,
  getRelativeValue,
  uniteResults,
  getFormattedLabels,
  getBodyRows,
  getCombinedTableProps
} from './service';

const {formatReportResult} = formatters;

export default withErrorHandling(
  class Table extends React.Component {
    state = {
      camundaEndpoints: null,
      needEndpoint: false
    };

    static getDerivedStateFromProps({report: {result, combined}}) {
      if (result && !combined && isRaw(result)) {
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

    processSingleData(labels, result, instanceCount) {
      const {formatter = v => v, report, flowNodeNames = {}} = this.props;
      const {
        configuration: {hideAbsoluteValue, hideRelativeValue},
        view: {property}
      } = report.data;

      const displayRelativeValue = property === 'frequency' && !hideRelativeValue;
      const displayAbsoluteValue = property === 'duration' || !hideAbsoluteValue;

      if (!displayAbsoluteValue) {
        labels.length = 1;
      }

      // normal two-dimensional data
      return {
        head: [...labels, ...(displayRelativeValue ? ['Relative Frequency'] : [])],
        body: Object.keys(result).map(key => [
          flowNodeNames[key] || key,
          ...(displayAbsoluteValue ? [formatter(result[key])] : []),
          ...(displayRelativeValue ? [getRelativeValue(result[key], instanceCount)] : [])
        ])
      };
    }

    processCombinedData() {
      const {formatter, report} = this.props;
      const {labels, reportsNames, combinedResult, processInstanceCount} = getCombinedTableProps(
        report.result,
        report.data.reportIds
      );
      const {
        configuration: {hideAbsoluteValue, hideRelativeValue}
      } = report.data;
      const {view} = Object.values(report.result)[0].data;

      const displayRelativeValue = view.property === 'frequency' && !hideRelativeValue;
      const displayAbsoluteValue = !hideAbsoluteValue;

      const keysLabel = labels[0][0];

      const formattedLabels = getFormattedLabels(
        labels,
        reportsNames,
        displayRelativeValue,
        displayAbsoluteValue
      );

      // get all unique keys of results of multiple reports
      let allKeys = Object.keys(Object.assign({}, ...combinedResult));

      // make all hash tables look exactly the same by filling empty keys with empty string
      const unitedResults = uniteResults(combinedResult, allKeys);

      // convert hashtables into a table rows array
      const rows = getBodyRows(
        unitedResults,
        allKeys,
        formatter,
        displayRelativeValue,
        processInstanceCount,
        displayAbsoluteValue
      );

      return {
        head: [keysLabel, ...formattedLabels],
        body: rows
      };
    }

    render() {
      const {
        report: {result},
        errorMessage,
        disableReportScrolling
      } = this.props;

      if (!result || typeof result !== 'object') {
        return <ReportBlankSlate errorMessage={errorMessage} />;
      }

      if (this.state.needEndpoint && this.state.camundaEndpoints === null) {
        return <LoadingIndicator />;
      }

      return (
        <TableRenderer disableReportScrolling={disableReportScrolling} {...this.formatData()} />
      );
    }

    formatData = () => {
      const {
        report: {reportType, combined, data, processInstanceCount, decisionInstanceCount, result},
        updateSorting
      } = this.props;

      const {
        parameters,
        configuration: {excludedColumns, columnOrder, xml},
        groupBy,
        view
      } = data;

      // Combined Report
      if (combined) return this.processCombinedData();

      const formattedResult = formatReportResult(data, result);

      // raw data
      if (isRaw(result)) {
        return {
          ...processRawData({
            data: formattedResult,
            excludedColumns,
            columnOrder,
            endpoints: this.state.camundaEndpoints,
            reportType
          }),
          updateSorting,
          sorting: parameters && parameters.sorting
        };
      }

      // Normal single Report
      const config = reportConfig[reportType];
      const labels = [
        config.getLabelFor(config.options.groupBy, groupBy, xml),
        config.getLabelFor(config.options.view, view, xml)
      ];

      return this.processSingleData(
        labels,
        formattedResult,
        processInstanceCount || decisionInstanceCount || 0
      );
    };
  }
);

function isRaw(data) {
  return data.length;
}
