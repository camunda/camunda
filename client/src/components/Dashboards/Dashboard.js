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
    this.setState({name : evt.target.value});
  }

  saveChanges = async evt => {
    await update(this.id, { name : this.state.name});
  }

  renderEditMode = (state) => {
    const {name, lastModifier, lastModified} = state;

    return (
      <div>
        <input id={'name'} onChange={this.updateName} value={name || ''}></input>
        <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
        <div>
          <Link id={'save'} to={`/dashboard/${this.id}`} onClick={this.saveChanges}>Save</Link> |
          <Link to={`/dashboard/${this.id}`}>Cancel</Link>
        </div>
      </div>
    )
  }

  renderViewMode = (state) => {
    const {name, lastModifier, lastModified} = state;

    return (
      <div>
        <h2>{name}</h2>
        <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
        <div>
          <Link id={'edit'} to={`/dashboard/${this.id}/edit`}>Edit</Link> |
          <button onClick={this.deleteDashboard}>Delete</button>
        </div>
      </div>
    )
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {loaded, redirect} = this.state;


    if(!loaded) {
      return <div>loading...</div>;
    }

    if(redirect) {
      return <Redirect to='/dashboards' />;
    }

    return (<div>
      {viewMode === 'edit' ? (this.renderEditMode(this.state)) : (this.renderViewMode(this.state))}
    </div>);
  }
}
