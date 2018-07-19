import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer} from 'components';
import {processRawData} from 'services';
import {withErrorHandling} from 'HOC';

import {getCamundaEndpoints, getRelativeValue} from './service';

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
      const {data, errorMessage, disableReportScrolling} = this.props;

      if (!data || typeof data !== 'object') {
        return <ReportBlankSlate message={errorMessage} />;
      }

      if (this.state.needEndpoint && this.state.camundaEndpoints === null) {
        return 'loading...';
      }

      return (
        <TableRenderer disableReportScrolling={disableReportScrolling} {...this.formatData()} />
      );
    }

    formatData = () => {
      const {
        formatter = v => v,
        labels,
        configuration: {excludedColumns, columnOrder},
        data,
        property,
        processInstanceCount
      } = this.props;

      if (isRaw(data)) {
        // raw data
        return processRawData(data, excludedColumns, columnOrder, this.state.camundaEndpoints);
      } else {
        // normal two-dimensional data
        if (property === 'frequency') {
          return {
            head: [...labels, 'Relative Frequency'],
            body: Object.keys(data).map(key => [
              key,
              formatter(data[key]),
              getRelativeValue(data[key], processInstanceCount)
            ])
          };
        } else {
          return {
            head: labels,
            body: Object.keys(data).map(key => [key, formatter(data[key])])
          };
        }
      }
    };
  }
);

function isRaw(data) {
  return data.length;
}
