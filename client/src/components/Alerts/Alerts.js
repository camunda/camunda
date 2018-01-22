import React from 'react';
import update from 'immutability-helper';

import {Button, Modal} from 'components';

import {loadAlerts, loadReports, saveNewAlert, deleteAlert, updateAlert} from './service';
import AlertModal from './AlertModal';

import './Alerts.css';

export default class Alerts extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      alerts: null,
      reports: null,
      alertToEdit: null,
      alertToDelete: null
    };

    this.loadData();
  }

  loadData = async () => {
    this.setState({
      alerts: await loadAlerts(),
      reports: (await loadReports()).filter(
        ({data: {visualization}}) => visualization === 'number'
      )
    });
  }

  saveAlert = async alert => {
    const alertToEdit = this.state.alertToEdit;
    if(alertToEdit.id) {
      // edit an existing alert
      this.setState(update(this.state, {
        alerts: {
          [this.state.alerts.indexOf(alertToEdit)]: {
            $set: alert
          }
        },
        alertToEdit: {$set: null}
      }));

      updateAlert(alertToEdit.id, alert);
    } else {
      // add a new alert
      this.setState({
        alerts: [...this.state.alerts, alert],
        alertToEdit: null
      });

      const newId = await saveNewAlert(alert);

      this.setState(update(this.state, {
        alerts: {
          [this.state.alerts.indexOf(alert)]: {
            id: {
              $set: newId
            }
          }
        }
      }));
    }
  }

  deleteAlert = async () => {
    deleteAlert(this.state.alertToDelete.id);

    this.setState({
      alerts: this.state.alerts.filter(alert => alert !== this.state.alertToDelete),
      alertToDelete: null
    });
  };

  editAlert = alertToEdit => () => this.setState({alertToEdit});
  openCreationModal = () => this.setState({alertToEdit: {}});
  cancelEdit = () => this.setState({alertToEdit: null});
  showDeleteModal = alert => () => this.setState({alertToDelete: alert});
  cancelDeleting = () => this.setState({alertToDelete: null});

  render() {
    return <div className="Alerts">
      <div className="Alerts__header">
        <h1 className="Alerts__heading">Alerts</h1>
        <div className="Alerts__tools">
          <Button color='green' className='Alerts__createButton' onClick={this.openCreationModal}>Create new Alert</Button>
        </div>
      </div>
      {this.state.alerts && this.state.reports ? this.renderList() : <div>loading...</div>}
      {this.state.reports &&
        <AlertModal reports={this.state.reports} onConfirm={this.saveAlert} onClose={this.cancelEdit} alert={this.state.alertToEdit} />
      }
      {this.state.alertToDelete &&
        <Modal open={true} onClose={this.cancelDeleting} className='EntityList__delete-modal'>
          <Modal.Header>Delete {this.state.alertToDelete.name}</Modal.Header>
          <Modal.Content>
            <p>You are about to delete {this.state.alertToDelete.name}. Are you sure you want to proceed?</p>
          </Modal.Content>
          <Modal.Actions>
            <Button className="EntityList__close-delete-modal-button" onClick={this.cancelDeleting}>Close</Button>
            <Button type="primary" color="red" className="EntityList__delete-entity-modal-button" onClick={this.deleteAlert}>Delete</Button>
          </Modal.Actions>
        </Modal>
      }
    </div>
  }

  renderList() {
    if(this.state.alerts.length === 0) {
      return (
        <ul className='Alert__list'>
          <li className="Alert__item Alert__no-entities">
            You have no Alerts configured yet.&nbsp;
            <a className='Alert__createLink' role='button' onClick={this.openCreationModal}>Create a new Alert…</a>
          </li>
        </ul>
      );
    } else {
      return (
        <ul className='Alert__list'>
          {this.state.alerts.map((alert, idx) => {
            return (<li key={idx} className='Alert__item'>
              <span className='Alert__data Alert__data--title' onClick={this.editAlert(alert)}>{alert.name}</span>
              <span className='Alert__data Alert__data--metadata'>
                Alert <span className='Alert__data--highlight'>{alert.email}</span>
                {' '}when Report <span className='Alert__data--highlight'>{this.state.reports.find(({id}) => alert.reportId === id).name}</span>
                {' '}has a value <span className='Alert__data--highlight'>{alert.thresholdOperator === '<' ? 'below' : 'above'}
                {' '}{alert.threshold}</span>
              </span>
              <span className='Alert__data Alert__data--tool'>
                <Button type='small' className='Alert__deleteButton' onClick={this.showDeleteModal(alert)}>Delete</Button>
              </span>
              <span className='Alert__data Alert__data--tool'>
                <Button type='small' className='Alert__editButton' onClick={this.editAlert(alert)}>Edit</Button>
              </span>
            </li>);
          })}
        </ul>
      );
    }
  }
}
