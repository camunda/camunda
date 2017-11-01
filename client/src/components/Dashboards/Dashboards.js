import React from 'react';
import moment from 'moment';
import {Redirect} from 'react-router-dom';

import {Table} from '../Table';

import {loadDashboards, create} from './service';

export default class Dashboards extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToDashboard: false,
      loaded: false
    };

    this.load();
  }

  load = async () => {
    const response = await loadDashboards();

    this.setState({
      data: response,
      loaded: true
    });
  }

  createDashboard = async evt => {
    this.setState({
      redirectToDashboard: await create()
    });
  }

  formatData = data => data.map(dashboard => [
    {content: dashboard.name || `Dashboard ${dashboard.id}`, link: `/dashboard/${dashboard.id}`},
    `Last modified at ${moment(dashboard.lastModified).format('lll')}`,
    `by ${dashboard.lastModifier}`,
    {content: 'Edit', link: `/dashboard/${dashboard.id}/edit`}
  ])

  render() {
    const {redirectToDashboard, loaded} = this.state;

    if(redirectToDashboard !== false) {
      return (<Redirect to={`/dashboard/${redirectToDashboard}/edit`} />);
    } else {
      return (<div>
        <button onClick={this.createDashboard}>Create New Dashboard</button>
        <h2>Dashboards</h2>
        {loaded ? (
          <Table data={this.formatData(this.state.data)} />
        ) : (
          <div>loading...</div>
        )}
      </div>);
    }
  }
}
