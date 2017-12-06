import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {Button, Modal, Input, ControlGroup, CopyToClipboard} from 'components';

import {loadDashboard, remove, update} from './service';

import DashboardView from './DashboardView';
import AddButton from './AddButton';
import Grid from './Grid';

import './Dashboard.css';

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
      originalName: null,
      reports: [],
      originalReports : [],
      modalVisible: false
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

  showModal = () => {
    this.setState({
      modalVisible: true
    });
  }

  closeModal = () => {
    this.setState({
      modalVisible: false
    });
  }

  renderEditMode = (state) => {
    const {name, lastModifier, lastModified} = state;

    return (
      <div className='Dashboard'>
        <div className='Dashboard__header'>
          <div className='Dashboard__name-container'>
            <Input type="text" id={'name'} onChange={this.updateName} value={name || ''} className='Dashboard__name-input' placeholder='Dashboard Name'></Input>
            <div className='Dashboard__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Dashboard__tools'>
            <Link className='Button Dashboard__tool-button Dashboard__save-button' to={`/dashboard/${this.id}`} onClick={this.saveChanges.bind(this)}>Save</Link>
            <Link className='Button Dashboard__tool-button Dashboard__cancel-button' to={`/dashboard/${this.id}`} onClick={this.cancelChanges}>Cancel</Link>
          </div>
        </div>
        <DashboardView reports={this.state.reports}>
          <Grid />
          <AddButton addReport={this.addReport} />
        </DashboardView>
      </div>
    )
  }


  renderViewMode = (state) => {
    const {name, lastModifier, lastModified, modalVisible} = state;

    return (
      <div className='Dashboard'>
        <div className='Dashboard__header'>
          <div className='Dashboard__name-container'>
            <h1 className='Dashboard__heading'>{name}</h1>
            <div className='Dashboard__metadata'>Last modified {moment(lastModified).format('lll')} by {lastModifier}</div>
          </div>
          <div className='Dashboard__tools'>
            <Link className='Button Dashboard__tool-button Dashboard__edit-button' to={`/dashboard/${this.id}/edit`}>Edit</Link>
            <Button onClick={this.deleteDashboard} className='Dashboard__tool-button Dashboard__delete-button'>Delete</Button>
            <Button onClick={this.showModal} className='Dashboard__tool-button Dashboard__share-button'>Share</Button>
          </div>
        </div>
        <Modal open={modalVisible} onClose={this.closeModal} className='Dashboard__share-modal'>
          <Modal.Header>Share {this.state.name}</Modal.Header>
          <Modal.Content>
            <ControlGroup>
              <CopyToClipboard value={document.URL} />
            </ControlGroup>
          </Modal.Content>
          <Modal.Actions>
            <Button className="Dashboard__close-share-modal-button" onClick={this.closeModal}>Close</Button>
          </Modal.Actions>
        </Modal>
        <DashboardView reports={this.state.reports} />
      </div>
    )
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

    return (<div>
      {viewMode === 'edit' ? (this.renderEditMode(this.state)) : (this.renderViewMode(this.state))}
    </div>);
  }
}
