import React from 'react';

import {ErrorBoundary} from '../ErrorBoundary';

import {Number, Json, Table} from './views';

export default class ReportView extends React.Component {
  render() {
    const {data} = this.props;

    let view;
    switch(data.visualization) {
      case 'number': view = <Number data={data.result} />; break;
      case 'table': view = <Table data={data.result} />; break;
      default: view = <Json data={data} />; break;
    }

    return (<ErrorBoundary>
      {view}
    </ErrorBoundary>);
  }
}
