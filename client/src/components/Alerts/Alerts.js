import React from 'react';
import {EntityList} from 'components';
import AlertModal from './AlertModal';

import {formatters, loadEntity} from 'services';

import './Alerts.css';
const {duration, frequency} = formatters;

export default class Alerts extends React.Component {
  state = {
    reports: null
  };

  componentDidMount = async () => {
    const reports = (await loadEntity('report')).filter(
      ({data: {visualization}}) => visualization === 'number'
    );
    this.setState({
      reports
    });
  };

  renderMetadata = alert => {
    if (!this.state.reports) return;
    const report = this.state.reports.find(({id}) => alert.reportId === id);
    return (
      <span className="metadata">
        Alert <span className="highlight">{alert.email}</span> when Report{' '}
        <span className="highlight">{report.name}</span> has a value{' '}
        <span className="highlight">
          {alert.thresholdOperator === '<' ? 'below ' : 'above '}
          {report.data.view.property === 'duration'
            ? duration(alert.threshold)
            : frequency(alert.threshold)}
        </span>
      </span>
    );
  };

  render() {
    return (
      <EntityList
        api="alert"
        label="Alert"
        sortBy={'lastModified'}
        operations={['create', 'edit', 'delete']}
        EditModal={AlertModal}
        renderCustom={this.renderMetadata}
      />
    );
  }
}
