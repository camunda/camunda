import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer, LoadingIndicator} from 'components';
import {processRawData} from 'services';
import {withErrorHandling} from 'HOC';

import {
  getCamundaEndpoints,
  getRelativeValue,
  uniteResults,
  getFormattedLabels,
  getBodyRows
} from './service';

export default withErrorHandling(
  class Table extends React.Component {
    state = {
      camundaEndpoints: null,
      needEndpoint: false
    };

    static getDerivedStateFromProps({data, reportType}) {
      if (data && (!reportType || reportType === 'single') && isRaw(data)) {
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

    processSingleData(labels, data, processInstanceCount) {
      const {
        formatter = v => v,
        property,
        configuration: {hideAbsoluteValue, hideRelativeValue}
      } = this.props;

      const displayRelativeValue = property === 'frequency' && !hideRelativeValue;
      const displayAbsoluteValue = !hideAbsoluteValue;

      if (hideAbsoluteValue) {
        labels.length = 1;
      }

      // normal two-dimensional data
      return {
        head: [...labels, ...(displayRelativeValue ? ['Relative Frequency'] : [])],
        body: Object.keys(data).map(key => [
          key,
          ...(displayAbsoluteValue ? [formatter(data[key])] : []),
          ...(displayRelativeValue ? [getRelativeValue(data[key], processInstanceCount)] : [])
        ])
      };
    }

    processCombinedData() {
      const {
        data,
        labels,
        reportsNames,
        processInstanceCount,
        formatter = v => v,
        property
      } = this.props;

      const isFrequency = property === 'frequency';

      const keysLabel = labels[0][0];

      const formattedLabels = getFormattedLabels(labels, reportsNames, isFrequency);

      // get all unique keys of results of multiple reports
      let allKeys = Object.keys(Object.assign({}, ...data));

      // make all hash tables look exactly the same by filling empty keys with empty string
      const unitedResults = uniteResults(data, allKeys);

      // convert hashtables into a table rows array
      const rows = getBodyRows(
        unitedResults,
        allKeys,
        formatter,
        isFrequency,
        processInstanceCount
      );

      return {
        head: [keysLabel, ...formattedLabels],
        body: rows
      };
    }

    render() {
      const {data, errorMessage, disableReportScrolling} = this.props;

      if (!data || typeof data !== 'object') {
        return <ReportBlankSlate message={errorMessage} />;
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
        configuration: {excludedColumns, columnOrder},
        labels = [],
        data,
        reportType = 'single',
        processInstanceCount
      } = this.props;

      // Combined Report
      if (reportType === 'combined') return this.processCombinedData();
      // raw data
      if (isRaw(data))
        return processRawData(data, excludedColumns, columnOrder, this.state.camundaEndpoints);
      // Normal single Report
      return this.processSingleData(labels, data, processInstanceCount);
    };
  }
);

function isRaw(data) {
  return data.length;
}
