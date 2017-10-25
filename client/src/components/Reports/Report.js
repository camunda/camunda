import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {loadSingleReport, remove} from './service';

export default class Report extends React.Component {
  constructor(props) {
    super(props);

    this.id = props.match.params.id;

    this.state = {
      name: null,
      lastModified: null,
      lastModifier: null,
      loaded: false,
      redirect: false
    };

    this.loadReport();
  }

  loadReport = async () => {
    const {name, lastModifier, lastModified} = await loadSingleReport(this.id);

    this.setState({
      name,
      lastModifier,
      lastModified,
      loaded: true
    });
  }

  deleteReport = async evt => {
    await remove(this.id);

    this.setState({
      redirect: true
    });
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {name, lastModified, lastModifier, loaded, redirect} = this.state;

    if(!loaded) {
      return <div>loading...</div>;
    }

    if(redirect) {
      return <Redirect to='/reports' />;
    }

    return (<div>
      <h2>{name}</h2>
      <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
      {viewMode === 'edit' ? (
        <div>
          <Link to={`/report/${this.id}`}>Save</Link> |
          <Link to={`/report/${this.id}`}>Cancel</Link>
        </div>
      ) : (
        <div>
          <Link to={`/report/${this.id}/edit`}>Edit</Link> |
          <button onClick={this.deleteReport}>Delete</button>
        </div>
      )}

    </div>);
  }
}
