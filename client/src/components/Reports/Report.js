import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {loadSingleReport, remove, getReportData, saveReport} from './service';
import ControlPanel from './ControlPanel';
import ReportView from './ReportView';

export default class Report extends React.Component {
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

    this.loadReport();
  }

  loadReport = async () => {
    const {name, lastModifier, lastModified, data} = await loadSingleReport(this.id);
    const reportResult = await getReportData(this.id);

    const stateData = data || {
      processDefinitionId: '',
      view: {operation: 'count', entity: 'processInstance'},
      groupBy: {type: 'none', unit: null},
      visualization: 'json',
      filter: null
    };

    this.setState({
      name,
      lastModifier,
      lastModified,
      data: stateData,
      originalData: stateData,
      reportResult,
      loaded: true,
      originalName: name
    });
  }

  deleteReport = async evt => {
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

  updateReport = async (field, newValue) => {
    const data = {
      ...this.state.data,
      [field]: newValue
    };

    this.setState({data});

    if(data.processDefinitionId) {
      const reportResult = await getReportData(data);

      this.setState({reportResult});
    }
  }

  save = async evt => {
    saveReport(this.id, {
      name: this.state.name,
      data: this.state.data
    });

    this.setState({
      originalData: {...this.state.data},
      originalName: this.state.name
    });
  }

  cancel = async () => {
    const reportResult = await getReportData(this.id);
    this.setState({
      name : this.state.originalName,
      data: {...this.state.originalData},
      reportResult
    });
  }

  renderEditMode = (state) => {
    const {name, lastModifier, lastModified, data} = state;

    return (
      <div>
        <input id={'name'} onChange={this.updateName} value={name || ''}></input>
        <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
        <div>
          <Link id={'save'} to={`/report/${this.id}`} onClick={this.save}>Save</Link> |
          <Link id={'cancel'} to={`/report/${this.id}`} onClick={this.cancel}>Cancel</Link>
          <ControlPanel {...data} onChange={this.updateReport} />
        </div>

        <ReportView data={state.reportResult} />
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
          <Link id={'edit'} to={`/report/${this.id}/edit`}>Edit</Link> |
          <button onClick={this.deleteReport}>Delete</button>
        </div>

        <ReportView data={state.reportResult} />
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
      return <Redirect to='/reports' />;
    }

    return (<div>
      {viewMode === 'edit' ? (this.renderEditMode(this.state)) : (this.renderViewMode(this.state))}
    </div>);
  }
}
