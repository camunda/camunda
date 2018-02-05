import React from 'react';
import moment from 'moment';
import {default as updateState} from 'immutability-helper';
import {Link, Redirect} from 'react-router-dom';

import {Button, Modal, Input, ControlGroup, CopyToClipboard} from 'components';

import {loadDashboard, remove, update} from './service';

import {DashboardView} from './DashboardView';
import {AddButton} from './AddButton';
import {Grid} from './Grid';
import {DimensionSetter} from './DimensionSetter';
import {DeleteButton} from './DeleteButton';
import {DragBehavior} from './DragBehavior';
import {ResizeHandle} from './ResizeHandle';

import './Dashboard.css';

export default class Dashboard extends React.Component {
  constructor(props) {
    super(props);

    this.id = props.match.params.id;
    this.isNew = (this.props.location.search === '?new');

    this.state = {
      name: null,
      lastModified: null,
      lastModifier: null,
      loaded: false,
      redirect: false,
      originalName: null,
      reports: [],
      originalReports : [],
      shareModalVisible: false,
      deleteModalVisible: false,
      addButtonVisible: true
    };

    this.load();
  }

  load = async () => {
    const {name, lastModifier, lastModified, reports} = await loadDashboard(this.id);

    this.setState({
      lastModifier,
      lastModified,
      loaded: true,
      name,
      originalName: name,
      reports: reports || [],
      originalReports: reports || []
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
      originalReports: this.state.reports
    });
  }

  cancelChanges = async () => {
    this.setState({
      name : this.state.originalName,
      reports: this.state.originalReports
    });
  }

  addReport = newReport => {
    this.setState({
      reports: [
        ...this.state.reports,
        newReport
      ]
    });
  }

  deleteReport = ({report: reportToRemove}) => {
    this.setState({
      reports: this.state.reports.filter(report => report !== reportToRemove)
    });
  }

  updateReport = ({report, ...changes}) => {
    const reportIdx = this.state.reports.indexOf(report);

    Object.keys(changes).forEach(prop => {
      changes[prop] = {$set: changes[prop]};
    });

    this.setState({
      reports: updateState(this.state.reports, {
        [reportIdx]: changes
      })
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

  showAddButton = () => {
    this.setState({
      addButtonVisible: true
    });
  }

  hideAddButton = () => {
    this.setState({
      addButtonVisible: false
    });
  }

  renderEditMode = (state) => {
    const {name, lastModifier, lastModified} = state;

    return (
      <div className='Dashboard'>
        <div className='Dashboard__header'>
          <div className='Dashboard__name-container'>
            <Input type="text" id={'name'} reference={this.inputRef} onChange={this.updateName} value={name || ''} className='Dashboard__name-input' placeholder='Dashboard Name'></Input>
            <div className='Dashboard__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Dashboard__tools'>
            <Link className='Button Dashboard__tool-button Dashboard__save-button' to={`/dashboard/${this.id}`} onClick={this.saveChanges.bind(this)}>Save</Link>
            <Link className='Button Dashboard__tool-button Dashboard__cancel-button' to={`/dashboard/${this.id}`} onClick={this.cancelChanges}>Cancel</Link>
          </div>
        </div>
        <DashboardView viewMode={false} reports={this.state.reports} reportAddons={[
          <DragBehavior key='DragBehavior' reports={this.state.reports} updateReport={this.updateReport} onDragStart={this.hideAddButton} onDragEnd={this.showAddButton} />,
          <DeleteButton key='DeleteButton' deleteReport={this.deleteReport} />,
          <ResizeHandle key='ResizeHandle' reports={this.state.reports} updateReport={this.updateReport} onResizeStart={this.hideAddButton} onResizeEnd={this.showAddButton} />
        ]}>
          <Grid reports={this.state.reports} />
          <DimensionSetter emptyRows={9} reports={this.state.reports} />
          <AddButton addReport={this.addReport} visible={this.state.addButtonVisible} />
        </DashboardView>
      </div>
    )
  }


  renderViewMode = (state) => {
    const {name, lastModifier, lastModified, shareModalVisible, deleteModalVisible} = state;

    return (
      <div className='Dashboard'>
        <div className='Dashboard__header'>
          <div className='Dashboard__name-container'>
            <h1 className='Dashboard__heading'>{name}</h1>
            <div className='Dashboard__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Dashboard__tools'>
            <Link className='Dashboard__tool-button Dashboard__edit-button' to={`/dashboard/${this.id}/edit`}><Button>Edit</Button></Link>
            <Button onClick={this.showDeleteModal} className='Dashboard__tool-button Dashboard__delete-button'>Delete</Button>
            <Button onClick={this.showShareModal} className='Dashboard__tool-button Dashboard__share-button'>Share</Button>
          </div>
        </div>
        <Modal open={shareModalVisible} onClose={this.closeShareModal} className='Dashboard__share-modal'>
          <Modal.Header>Share {this.state.name}</Modal.Header>
          <Modal.Content>
            <ControlGroup>
              <CopyToClipboard value={document.URL} />
            </ControlGroup>
          </Modal.Content>
          <Modal.Actions>
            <Button className="Dashboard__close-share-modal-button" onClick={this.closeShareModal}>Close</Button>
          </Modal.Actions>
        </Modal>
        <Modal open={deleteModalVisible} onClose={this.closeDeleteModal} className='Dashboard__delete-modal'>
          <Modal.Header>Delete {this.state.name}</Modal.Header>
          <Modal.Content>
            <p>You are about to delete {this.state.name}. Are you sure you want to proceed?</p>
          </Modal.Content>
          <Modal.Actions>
            <Button className="Dashboard__close-delete-modal-button" onClick={this.closeDeleteModal}>Cancel</Button>
            <Button type="primary" color="red" className="Dashboard__delete-dashboard-modal-button" onClick={this.deleteDashboard}>Delete</Button>
          </Modal.Actions>
        </Modal>
        <DashboardView viewMode={true} reports={this.state.reports}>
          <DimensionSetter reports={this.state.reports} />
        </DashboardView>
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
      return <div className = 'dashboard-loading-indicator'>loading...</div>;
    }

    if(redirect) {
      return <Redirect to='/dashboards' />;
    }

    return (<div style={{display: 'flex', flexDirection: 'column', flexGrow: 1}}>
      {viewMode === 'edit' ? (this.renderEditMode(this.state)) : (this.renderViewMode(this.state))}
    </div>);
  }
}
