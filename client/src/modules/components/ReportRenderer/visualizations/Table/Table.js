import React from 'react';
import ReportBlankSlate from '../../ReportBlankSlate';
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

    render() {
      const {report, errorMessage, disableReportScrolling, updateReport} = this.props;

      if (!report.result || typeof report.result !== 'object') {
        return <ReportBlankSlate errorMessage={errorMessage} />;
      }

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
      this.props.updateReport({parameters: {sorting: {$set: {by, order}}}}, true);
    };

    formatData = () => {
      const {
        report: {reportType, combined, data, result},
        updateReport
      } = this.props;
      const {parameters} = data;

      // Combined Report
      if (combined) return processCombinedData(this.props);

      // raw data
      if (isRaw(result)) {
        return {
          ...processRawData[reportType](this.props, this.state.camundaEndpoints),
          updateSorting: updateReport && this.updateSorting,
          sorting: parameters && parameters.sorting
        };
      }

      // Normal single Report
      return processDefaultData(this.props);
    };
  }
);

function isRaw(data) {
  return data.length;
}
