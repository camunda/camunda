import React from 'react';
import moment from 'moment';
import {Redirect} from 'react-router-dom';

import {Table} from '../Table';

import {loadReports, create, remove} from './service';

export default class Reports extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToReport: false,
      loaded: false
    };

    this.loadReports();
  }

  loadReports = async () => {
    const response = await loadReports();

    this.setState({
      data: response,
      loaded: true
    });
  }

  deleteReport = id => evt => {
    remove(id);

    this.setState({
      data: this.state.data.filter(report => report.id !== id)
    })
  };

  createReport = async evt => {
    this.setState({
      redirectToReport: await create()
    });
  }

  formatData = data => data.map(report => [
    {content: report.name || `Report ${report.id}`, link: `/report/${report.id}`},
    `Last modified at ${moment(report.lastModified).format('lll')}`,
    `by ${report.lastModifier}`,
    {content: 'Delete', onClick: this.deleteReport(report.id)},
    {content: 'Edit', link: `/report/${report.id}/edit`}
  ])

  render() {
    const {redirectToReport, loaded} = this.state;

    if(redirectToReport !== false) {
      return (<Redirect to={`/report/${redirectToReport}/edit`} />);
    } else {
      return (<div>
        <button onClick={this.createReport}>Create New Report</button>
        <h2>Reports</h2>
        {loaded ? (
          <Table data={this.formatData(this.state.data)} />
        ) : (
          <div>loading...</div>
        )}
      </div>);
    }
  }
}
