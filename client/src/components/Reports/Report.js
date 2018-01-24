import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';
import {Button, Modal, Input, CopyToClipboard, ReportView} from 'components';

import {loadSingleReport, remove, getReportData, saveReport} from './service';
import ControlPanel from './ControlPanel';

import './Report.css';

export default class Report extends React.Component {
  constructor(props) {
    super(props);

    this.id = props.match.params.id;
    this.isNew = (props.location.search === '?new');

    this.state = {
      name: null,
      lastModified: null,
      lastModifier: null,
      loaded: false,
      redirect: false,
      originalName: null,
      shareModalVisible: false,
      deleteModalVisible: false
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
    if(this.allFieldsAreSelected(data)) {
      reportResult = await getReportData(data);
    }
    if (!reportResult) {
      reportResult = {data};
    }
    this.setState({reportResult});
  }

  allFieldsAreSelected = (data) => {
    const {processDefinitionId, view, groupBy, visualization} = data;
    return this.isNotEmpty(processDefinitionId) &&
    (this.viewGroupbyAndVisualizationFieldsAreSelected(view, groupBy, visualization) ||
    this.rawDataCombinationIsSelected(view.operation, visualization));
  }

  viewGroupbyAndVisualizationFieldsAreSelected = (view, groupBy, visualization) => {
    const operation = view.operation;
    const type = groupBy.type;
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
    let reportResult = await getReportData(this.id);
    if (!reportResult) {
      reportResult = {data: this.state.originalData};
    }
    this.setState({
      name : this.state.originalName,
      data: {...this.state.originalData},
      reportResult
    });
  }

  showShareModal = () => {
    this.setState({
      shareModalVisible: true
    });
  }

  closeShareModal = () => {
    this.setState({
      shareModalVisible: false
    });
  }

  showDeleteModal = () => {
    this.setState({
      deleteModalVisible: true
    });
  }

  closeDeleteModal = () => {
    this.setState({
      deleteModalVisible: false
    });
  }

  renderEditMode = () => {
    const {name, lastModifier, lastModified, data, reportResult} = this.state;
    return (
      <div className='Report'>
        <div className='Report__header'>
          <div className='Report__name-container'>
            <Input id='name' type='text' reference={this.inputRef} onChange={this.updateName} value={name || ''} className='Report__name-input' placeholder='Report Name'></Input>
            <div className='Report__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Report__tools'>
            <Link className='Button Report__tool-button Report__save-button' to={`/report/${this.id}`} onClick={this.save}>Save</Link>
            <Link className='Button Report__tool-button Report__cancel-button' to={`/report/${this.id}`} onClick={this.cancel}>Cancel</Link>
          </div>
        </div>

        <ControlPanel {...data} onChange={this.updateReport} />
        <div className='Report__content' >
          <ReportView report={reportResult} />
        </div>
      </div>
    )
  }

  renderViewMode = () => {
    const {name, lastModifier, lastModified, reportResult, shareModalVisible, deleteModalVisible} = this.state;

    return (
      <div className='Report'>
        <div className='Report__header'>
          <div className='Report__name-container'>
            <h1 className='Report__name'>{name}</h1>
            <div className='Report__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Report__tools'>
            <Link className='Report__tool-button Report__edit-button' to={`/report/${this.id}/edit`}><Button>Edit</Button></Link>
            <Button className='Report__tool-button Report__delete-button' onClick={this.showDeleteModal}>Delete</Button>
            <Button onClick={this.showShareModal} className='Report__tool-button Report__share-button'>Share</Button>
          </div>
        </div>
        <Modal open={shareModalVisible} onClose={this.closeShareModal} className='Report__share-modal'>
          <Modal.Header>Share {this.state.name}</Modal.Header>
          <Modal.Content>
            <CopyToClipboard value={document.URL} />
          </Modal.Content>
          <Modal.Actions>
            <Button className="Report__close-share-modal-button" onClick={this.closeShareModal}>Close</Button>
          </Modal.Actions>
        </Modal>
        <Modal open={deleteModalVisible} onClose={this.closeDeleteModal} className='Report__delete-modal'>
          <Modal.Header>Delete {this.state.name}</Modal.Header>
          <Modal.Content>
            <p>You are about to delete {this.state.name}. Are you sure you want to proceed?</p>
          </Modal.Content>
          <Modal.Actions>
            <Button className="Report__close-delete-modal-button" onClick={this.closeDeleteModal}>Close</Button>
            <Button type="primary" color="red" className="Report__delete-report-modal-button" onClick={this.deleteReport}>Delete</Button>
          </Modal.Actions>
        </Modal>
        <div className='Report__content' >
          <ReportView report={reportResult} />
        </div>
      </div>
    )
  }

  inputRef = (input) => {
    this.nameInput = input;
  }

  componentDidUpdate() {
    if(this.nameInput && this.isNew) {
      this.nameInput.focus();
      this.nameInput.select();
      this.isNew = false;
    }
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

    return (<div style={{flexDirection: 'column', display: 'flex', flexGrow: 1}}>
      {viewMode === 'edit' ? (this.renderEditMode()) : (this.renderViewMode())}
    </div>);
  }
}
