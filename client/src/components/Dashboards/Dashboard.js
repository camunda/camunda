import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {loadDashboard, remove, update} from './service';

export default class Dashboard extends React.Component {
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

    this.load();
  }

  load = async () => {
    const {name, lastModifier, lastModified} = await loadDashboard(this.id);

    this.setState({
      name,
      lastModifier,
      lastModified,
      loaded: true
    });
  }

  deleteDashboard = async evt => {
    await remove(this.id);

    this.setState({
      redirect: true
    });
  }

  updateName = evt => {
    const currentState = this.state;
    currentState.name = evt.currentTarget.value;
    this.setState(currentState);
  }

  saveChanges = async evt => {
    console.log('sending name [' + this.state.name + '] to the backend');
    await update(this.id, this.state);
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {name, lastModified, lastModifier, loaded, redirect} = this.state;

    if(!loaded) {
      return <div>loading...</div>;
    }

    if(redirect) {
      return <Redirect to='/dashboards' />;
    }

    return (<div>
      {viewMode === 'edit' ? (
        <input onChange={this.updateName} value={name}></input>
      ) : (
        <h2>{name}</h2>
      )}
      <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
      {viewMode === 'edit' ? (
        <div>
          <Link to={`/dashboard/${this.id}`} onClick={this.saveChanges}>Save</Link> |
          <Link to={`/dashboard/${this.id}`}>Cancel</Link>
        </div>
      ) : (
        <div>
          <Link to={`/dashboard/${this.id}/edit`}>Edit</Link> |
          <button onClick={this.deleteDashboard}>Delete</button>
        </div>
      )}

    </div>);
  }
}
