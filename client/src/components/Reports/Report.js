import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {loadSingleReport, remove, getReportData, saveReport} from './service';
import ControlPanel from './ControlPanel';
import ReportView from './ReportView';

import './Report.css';

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

  initializeReport = async () => {
    return ({
      processDefinitionId: 'Please select process instance',
      view: {operation: 'Please select a value', entity: 'processInstance', property: 'frequency'},
      groupBy: {type: 'Please select a value', unit: null},
      visualization: 'Please select a value',
      filter: []
    });
  }

  loadReport = async () => {
    const {name, lastModifier, lastModified, data} = await loadSingleReport(this.id);

    let stateData;
    let reportResult;

    if (data) {
      stateData = data;
      try {
        reportResult = await getReportData(this.id);
      } catch(error) {
        reportResult = null;
      }
    } else {
      stateData = await this.initializeReport();
      reportResult = null;
    }

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

    this.save();
  }

  deleteReport = async evt => {
    await remove(this.id);

    this.setState({
      redirect: true
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

      let reportResult;
      try {
        reportResult = await getReportData(data);
      } catch(error) {
        reportResult = null;
      }

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
    let reportResult;
    try {
      reportResult = await getReportData(this.id);
    } catch(e) {
      reportResult = null;
    }
    this.setState({
      name : this.state.originalName,
      data: {...this.state.originalData},
      reportResult
    });
  }

  renderEditMode = () => {
    const {name, lastModifier, lastModified, data, reportResult} = this.state;

    return (
      <div className='Report'>
        <div className='Report__header'>
          <div className='Report__name-container'>
            <input id='name' type='text' onChange={this.updateName} value={name || ''} className='Input Report__name-input'></input>
            <div className='Report__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Report__tools'>
            <Link id='save' className='Button Report__tool-button' to={`/report/${this.id}`} onClick={this.save}>Save</Link>
            <Link id='cancel' className='Button Report__tool-button' to={`/report/${this.id}`} onClick={this.cancel}>Cancel</Link>
          </div>
        </div>

        <ControlPanel {...data} onChange={this.updateReport} />
        <ReportView report={reportResult} />
      </div>
    )
  }

  renderViewMode = () => {
    const {name, lastModifier, lastModified, reportResult} = this.state;

    return (
      <div className='Report'>
        <div className='Report__header'>
          <div className='Report__name-container'>
            <h1 className='Report__name'>{name}</h1>
            <div className='Report__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Report__tools'>
            <Link id='edit' className='Button Report__tool-button' to={`/report/${this.id}/edit`}>Edit</Link>
            <button className='Button Report__tool-button' onClick={this.deleteReport}>Delete</button>
          </div>
        </div>

        <ReportView report={reportResult} />
      </div>
    )
  }


  render() {
    const {viewMode} = this.props.match.params;

    const {loaded, redirect} = this.state;

    if(!loaded) {
      return <div className='report-loading-indicator'>loading...</div>;
    }

    if(redirect) {
      return <Redirect to='/reports' />;
    }

    return (<div>
      {viewMode === 'edit' ? (this.renderEditMode()) : (this.renderViewMode())}
    </div>);
  }
}
