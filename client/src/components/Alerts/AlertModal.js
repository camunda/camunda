import React from 'react';
import update from 'immutability-helper';

import {Modal, Button, Input, Select} from 'components';
import {emailNotificationIsEnabled} from './service';

import './AlertModal.css';

const newAlert = {
  name: 'New Alert',
  email: '',
  reportId: '',
  thresholdOperator: '>',
  threshold: '100',
  checkInterval: {
    value: '10',
    unit: 'minutes'
  },
  reminder: null,
  fixNotification: false
};

export default class AlertModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = newAlert;
    
    this.checkIfEmailNotificationIsConfigured();
  }

  checkIfEmailNotificationIsConfigured = async () => {
    this.setState({
      emailNotificationIsEnabled: await emailNotificationIsEnabled()
    })
  }

  componentWillReceiveProps({alert}) {
    // set initial state after opening modal
    if(this.props.alert !== alert) {
      this.setState((alert && alert.id && {
        ...alert,
        threshold: alert.threshold.toString(),
        checkInterval: {
          value: alert.checkInterval.value.toString(),
          unit: alert.checkInterval.unit
        },
        reminder: alert.reminder ? {
          value: alert.reminder.value.toString(),
          unit: alert.reminder.unit
        } : null
      }) || newAlert)
    }
  }

  updateReminder = ({target: {checked}}) => {
    if(checked) {
      this.setState({
        reminder: {
          value: '2',
          unit: 'hours'
        }
      });
    } else {
      this.setState({
        reminder: null
      });
    }
  }

  alertValid = () => {
    return (
      this.state.name.trim() &&
      this.state.email.match(/^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/) && // taken from https://www.w3.org/TR/2012/WD-html-markup-20120320/input.email.html#input.email.attrs.value.single
      this.state.reportId &&
      this.state.threshold.trim() && !isNaN(this.state.threshold.trim()) &&
      this.state.checkInterval.value.trim() && !isNaN(this.state.checkInterval.value.trim()) && +this.state.checkInterval.value > 0 &&
      (this.state.reminder === null || (this.state.reminder.value.trim() && !isNaN(this.state.reminder.value.trim()) && +this.state.reminder.value > 0))
    );
  }

  confirm = () => {
    this.props.onConfirm(this.state);
  }

  isInEditingMode = () => {
    return this.props.alert && this.props.alert.id;
  }

  render() {
    const {name, email, reportId, thresholdOperator, threshold, 
      checkInterval, reminder, fixNotification, emailNotificationIsEnabled} = this.state;

    return <Modal open={this.props.alert} onClose={this.props.onClose}>
      <Modal.Header>
        {this.isInEditingMode() ?
        'Edit Alert' :
        'Add new Alert'}
      </Modal.Header>
      <Modal.Content>
        <div className="AlertModal__topSection">
          <div className="AlertModal__inputGroup">
            { !emailNotificationIsEnabled && 
              <span className={'AlertModal__configuration-warning'}>Email notification service is not configured. 
                Please check the {<a href="https://docs.camunda.org/optimize/latest/technical-guide/configuration/#alerting">Optimize documentation</a>}
              </span>
            }
            <label>
              <span className="AlertModal__label">Name</span>
              <Input className="AlertModal__input" value={name} onChange={({target: {value}}) => this.setState({name: value})}/>
            </label>
          </div>
          <div className="AlertModal__inputGroup">
            <label>
              <span className="AlertModal__label">Send Email to</span>
              <Input className="AlertModal__input" value={email} onChange={({target: {value}}) => this.setState({email: value})} />
            </label>
          </div>
          <div className="AlertModal__inputGroup">
            <label>
              <span className="AlertModal__label">when Report</span>
              <Select className="AlertModal__input" value={reportId} onChange={({target: {value}}) => this.setState({reportId: value})}>
                <Select.Option disabled value=''>Please select Report</Select.Option>
                {this.props.reports.map(({id, name}) => {
                  return <Select.Option key={id} value={id}>{name}</Select.Option>
                })}
              </Select>
            </label>
          </div>
          <div className="AlertModal__inputGroup">
            <label>
              <span className="AlertModal__label">has a value</span>
              <div className="AlertModal__combinedInput">
                <Select value={thresholdOperator} onChange={({target: {value}}) => this.setState({thresholdOperator: value})}>
                  <Select.Option value='>'>above</Select.Option>
                  <Select.Option value='<'>below</Select.Option>
                </Select>
                <Input className="AlertModal__input" value={threshold} onChange={({target: {value}}) => this.setState({threshold: value})} />
              </div>
            </label>
          </div>
        </div>
        <div className="AlertModal__inputGroup">
          <label>
            <span className="AlertModal__label">Check Report every</span>
            <div className="AlertModal__combinedInput">
              <Input
                className="AlertModal__input"
                value={checkInterval.value}
                onChange={({target: {value}}) => this.setState(update(this.state, {checkInterval: {value: {$set: value}}}))}
              />
              <Select
                value={checkInterval.unit}
                onChange={({target: {value}}) => this.setState(update(this.state, {checkInterval: {unit: {$set: value}}}))}
              >
                <Select.Option value='seconds'>Seconds</Select.Option>
                <Select.Option value='minutes'>Minutes</Select.Option>
                <Select.Option value='hours'>Hours</Select.Option>
                <Select.Option value='days'>Days</Select.Option>
                <Select.Option value='weeks'>Weeks</Select.Option>
                <Select.Option value='months'>Months</Select.Option>
              </Select>
            </div>
          </label>
        </div>
        <div className="AlertModal__inputGroup">
          <label>
            <Input type='checkbox' checked={fixNotification} onChange={({target: {checked}}) => this.setState({fixNotification: checked})} />
            Send Fix Notification
          </label>
        </div>
        <div className="AlertModal__inputGroup">
          <label>
            <Input type='checkbox' checked={!!reminder} onChange={this.updateReminder} />
            Send Reminder Mails
          </label>
          {reminder &&
            <div className="AlertModal__inputGroup">
              <label>
                <span className="AlertModal__label">every</span>
                <div className="AlertModal__combinedInput">
                  <Input
                    className="AlertModal__input"
                    value={reminder.value}
                    onChange={({target: {value}}) => this.setState(update(this.state, {reminder: {value: {$set: value}}}))}
                  />
                  <Select
                    value={reminder.unit}
                    onChange={({target: {value}}) => this.setState(update(this.state, {reminder: {unit: {$set: value}}}))}
                  >
                    <Select.Option value='minutes'>Minutes</Select.Option>
                    <Select.Option value='hours'>Hours</Select.Option>
                    <Select.Option value='days'>Days</Select.Option>
                    <Select.Option value='weeks'>Weeks</Select.Option>
                    <Select.Option value='months'>Months</Select.Option>
                  </Select>
                </div>
              </label>
            </div>
          }
        </div>
      </Modal.Content>
      <Modal.Actions>
        <Button onClick={this.props.onClose}>Cancel</Button>
        <Button type='primary' color='blue' onClick={this.confirm} disabled={!this.alertValid()}>
          {this.isInEditingMode() ?
          'Apply Changes' :
          'Add Alert'}
        </Button>
      </Modal.Actions>
    </Modal>
  }
}
