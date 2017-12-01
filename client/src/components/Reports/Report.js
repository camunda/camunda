import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';
import {Button, Modal} from 'components';

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
      originalName: null,
      modalVisible: false,
      modalText: null
    };

    this.loadReport();
  }

  initializeReport = () => {
    return ({
      processDefinitionId: '',
      view: {operation: '', entity: '', property: ''},
      groupBy: {type: '', unit: null},
      visualization: '',
      filter: []
    });
  }

  loadReport = async () => {
    const {name, lastModifier, lastModified, data} = await loadSingleReport(this.id);

    const reportResult = await getReportData(this.id);
    const stateData = data || this.initializeReport();

    this.setState({
      name,
      lastModifier,
      lastModified,
      data: stateData,
      originalData: stateData,
      reportResult: reportResult || {data: stateData},
      loaded: true,
      originalName: name
    }, this.save);
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

    let reportResult;
    if(this.areAllFieldsSelected(data)) {
      reportResult = await getReportData(data);
    }
    if (!reportResult) {
      reportResult = {data};
    }
    this.setState({reportResult});
  }

  areAllFieldsSelected = (data) => {
    const {processDefinitionId, view, groupBy, visualization} = data;
    return this.isNotEmpty(processDefinitionId) &&
    (this.allRemainingFieldsAreSelected(view.operation, groupBy.type, visualization) ||
    this.rawDataCombinationIsSelected(view.operation, visualization));
  }

  allRemainingFieldsAreSelected = (operation, type, visualization) => {
    return this.isNotEmpty(operation) &&
    this.isNotEmpty(type) &&
    this.isNotEmpty(visualization);
  }

  rawDataCombinationIsSelected = (operation, visualization) => {
    return operation === 'rawData' && this.isNotEmpty(visualization);
  }

  isNotEmpty = str => {
    return str !== null && str.length > 0;
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

  showModal = () => {
    this.setState({
      modalVisible: true
    });
  }

  closeModal = () => {
    this.setState({
      modalVisible: false,
      modalText: null
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
            <Button id='delete' className='Report__tool-button' onClick={this.deleteReport}>Delete</Button>
            <Button id='share' onClick={this.showModal} className='Report__tool-button'>Share</Button>
          </div>
        </div>
        {
          (this.state && this.state.modalVisible) ?
        <Modal open={true} onClose={this.closeModal}>
          <Modal.Header>Share {this.state.name}</Modal.Header>
          <Modal.Content>
              <input ref={this.textArea} readOnly value={document.URL}></input>
              <button id='copy-text-button' onClick={this.copyText}>Copy</button>
          </Modal.Content>
          <Modal.Actions>
            <Button id="close-shareModal-button" onClick={this.closeModal}>Close</Button>
          </Modal.Actions>
        </Modal> : ''
        }

        <ReportView report={reportResult} />
      </div>
    )
  }

  textArea = modalText => {
    this.setState({modalText});
  }

  copyText = () => {
    this.state.modalText.select();
    document.execCommand("Copy");
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {loaded, redirect, modalVisible, modalText} = this.state;

    if(modalVisible && modalText) {
      modalText.select();
    }

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
