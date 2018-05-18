import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer} from 'components';
import {processRawData} from 'services';
import {withErrorHandling} from 'HOC';

import {getCamundaEndpoints} from './service';

export default withErrorHandling(
  class Table extends React.Component {
    state = {
      camundaEndpoints: null,
      needEndpoint: false
    };

    static getDerivedStateFromProps({data}) {
      if (data && isRaw(data)) {
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
      const {
        data,
        configuration: {excludedColumns, columnOrder},
        formatter = v => v,
        labels,
        errorMessage,
        disableReportScrolling
      } = this.props;

      if (!data || typeof data !== 'object') {
        return <ReportBlankSlate message={errorMessage} />;
      }

      if (this.state.needEndpoint && this.state.camundaEndpoints === null) {
        return 'loading...';
      }

      return (
        <TableRenderer
          disableReportScrolling={disableReportScrolling}
          {...formatData(
            data,
            formatter,
            labels,
            excludedColumns,
            columnOrder,
            this.state.camundaEndpoints
          )}
        />
      );
    }
  }
);

function isRaw(data) {
  return data.length;
}

export function formatData(data, formatter, labels, excludedColumns, columnOrder, endpoint) {
  if (isRaw(data)) {
    // raw data
    return processRawData(data, excludedColumns, columnOrder, endpoint);
  } else {
    // normal two-dimensional data
    const body = Object.keys(data).map(key => [key, formatter(data[key])]);
    return {head: labels, body};
  }
}
