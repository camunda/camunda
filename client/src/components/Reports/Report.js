import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {loadSingleReport, remove, update, getReportData, saveReport} from './service';
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
      redirect: false
    };

    this.loadReport();
  }

  loadReport = async () => {
    const {name, lastModifier, lastModified, data} = await loadSingleReport(this.id);
    const reportResult = await getReportData(this.id);

    const mockedData = {
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
      // data: data || { //TODO: use this as soon as backend stores the correct info
      //   processDefinitionId: '',
      //   view: {operation: 'count', entity: 'processInstance'},
      //   groupBy: {type: 'none', unit: null},
      //   visualization: 'json',
      //   filter: null
      // },
      // originalData: data,
      data: mockedData,
      originalData: mockedData,
      reportResult,
      loaded: true
    });
  }

  deleteReport = async evt => {
    await remove(this.id);

    this.setState({
      redirect: true
    });
  }

  updateName = evt => {
    this.setState({name : evt.target.value});
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
      originalData: {...this.state.data}
    });
  }

  cancel = async evt => {
    const reportResult = await getReportData(this.id);

    this.setState({
      data: {...this.state.originalData},
      reportResult
    });
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {name, lastModified, lastModifier, data, loaded, redirect} = this.state;

    if(!loaded) {
      return <div>loading...</div>;
    }

    if(redirect) {
      return <Redirect to='/reports' />;
    }

    return (<div>
      {viewMode === 'edit' ? (
          <input id={'name'} onChange={this.updateName} value={name}></input>
      ) : (
          <h2>{name}</h2>
      )}
      <div>{moment(lastModified).format('lll')} | {lastModifier}</div>
      {viewMode === 'edit' ? (
        <div>
          <Link id={'save'} to={`/report/${this.id}`} onClick={this.save}>Save</Link> |
          <Link to={`/report/${this.id}`} onClick={this.cancel}>Cancel</Link>
          <ControlPanel {...data} onChange={this.updateReport} />
        </div>
      ) : (
        <div>
          <Link id={'edit'} to={`/report/${this.id}/edit`}>Edit</Link> |
          <button onClick={this.deleteReport}>Delete</button>
        </div>
      )}

      <ReportView data={this.state.reportResult} />
    </div>);
  }
}
