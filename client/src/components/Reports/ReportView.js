import React from 'react';

import {ErrorBoundary} from '../ErrorBoundary';

import {Number, Json, Table, Heatmap, Chart} from './views';

export default class ReportView extends React.Component {
  render() {
    const {data} = this.props;

    let view;
    switch(data.visualization) {
      case 'number': view = <Number data={data.result} />; break;
      case 'table': view = <Table data={data.result} />; break;
      case 'heat': view = <Heatmap process={data.processDefinitionId} data={data.result} />; break;
      case 'bar':
      case 'line':
      case 'pie':
        view = <Chart type={data.visualization} data={data.result} />; break;
      default: view = <Json data={data} />; break;
    }

    return (<ErrorBoundary>
      {view}
    </ErrorBoundary>);
  }
}
