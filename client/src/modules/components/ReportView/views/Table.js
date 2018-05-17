import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer} from 'components';
import {processRawData} from 'services';

import {getCamundaEndpoint} from './service';

export default class Table extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      camundaEndpoint: null
    };
  }

  async componentDidMount() {
    this.setState({
      camundaEndpoint: await getCamundaEndpoint()
    });
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

    if (this.state.camundaEndpoint === null) {
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
          this.state.camundaEndpoint
        )}
      />
    );
  }
}

export function formatData(data, formatter, labels, excludedColumns, columnOrder, endpoint) {
  if (data.length) {
    // raw data
    return processRawData(data, excludedColumns, columnOrder, endpoint);
  } else {
    // normal two-dimensional data
    const body = Object.keys(data).map(key => [key, formatter(data[key])]);
    return {head: labels, body};
  }
}
