import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {loadDashboard, remove, update} from './service';
import './Dashboard.css';
import EditGrid from './ReportGrid/EditGrid';

export default class Dashboard extends React.Component {
  constructor(props) {
    super(props);

    this.id = props.match.params.id;

    this.state = {
      name: null,
      lastModified: null,
      lastModifier: null,
      loaded: false,
      redirect: false,
      originalName: null
    };

    this.load();
  }

  load = async () => {
    const {name, lastModifier, lastModified, reports} = await loadDashboard(this.id);

    this.setState({
      name,
      lastModifier,
      lastModified,
      loaded: true,
      originalName: name,
      reports: reports ? reports : [],
      originalReports: reports ? reports.slice() : []
    });
  }

  deleteDashboard = async evt => {
    await remove(this.id);

    this.setState({
      redirect: true
    });
  }

  updateName = evt => {

    this.setState({
      name : evt.target.value
    });
  }

  saveChanges = async () => {
    await update(this.id, {
      name : this.state.name,
      reports : this.state.reports
    });

    this.setState({
      originalName: this.state.name,
      originalReports: this.state.reports ? this.state.reports.slice() : []
    });
  }

  cancelChanges = async () => {
    this.setState({
      name : this.state.originalName,
      reports: this.state.originalReports? this.state.originalReports.slice() : []
    });
  }

  handleReportSelection = (report, position, dimensions) => {
    this.state.reports.push({
      id : report.id,
      dimensions: dimensions,
      position: position,
      name: report.name
    });
  }

  handleReportRemoval = (report) => {
    console.log('removing report [' + report.id + '] from dashboard')
  }

  renderEditMode = (state) => {
    const {name, lastModifier, lastModified} = state;

    return (
      <div className={'dashboard'}>
        <div className={'navigation'}>
          <input type="text" id={'name'} onChange={this.updateName} value={name || ''}></input>
          <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
          <div>
            <Link id={'save'} className="Button" to={`/dashboard/${this.id}`} onClick={this.saveChanges.bind(this)}>Save</Link>
            <Link id={'cancel'} className="Button" to={`/dashboard/${this.id}`} onClick={this.cancelChanges}>Cancel</Link>
          </div>
        </div>
        <EditGrid
            reports={state.reports}
            onReportSelected={this.handleReportSelection.bind(this)}
            onReportRemoved={this.handleReportRemoval.bind(this)}
        />
      </div>
    )
  }


  renderViewMode = (state) => {
    const {name, lastModifier, lastModified} = state;

    return (
      <div>
        <h1>{name}</h1>
        <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
        <div>
          <Link id={'edit'} className="Button" to={`/dashboard/${this.id}/edit`}>Edit</Link>
          <button className="Button" onClick={this.deleteDashboard}>Delete</button>
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
