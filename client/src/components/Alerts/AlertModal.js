import React from 'react';

import {Modal, Button, Input, Select} from 'components';

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

  update = (field, nestedProp) => ({target: {value, type, checked}}) => {
    let parsedValue = type === 'checkbox' ? checked : value;

    if(field === 'reminder') {
      // special case: reminder takes null instead of false and has to create an object
      if(parsedValue === false) {
        parsedValue = null;
      } else if(parsedValue === true) {
        parsedValue = {
          value: '2',
          unit: 'hours'
        };
      }
    }

    this.setState({
      [field]: nestedProp ? {
        ...this.state[field],
        [nestedProp]: parsedValue
      } : parsedValue
    });
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
    const {name, email, reportId, thresholdOperator, threshold, checkInterval, reminder, fixNotification} = this.state;

    return <Modal open={this.props.alert} onClose={this.props.onClose}>
      <Modal.Header>
        {this.isInEditingMode() ?
        'Edit Alert' :
        'Add new Alert'}
      </Modal.Header>
      <Modal.Content>
        <div className="AlertModal__topSection">
          <div className="AlertModal__inputGroup">
            <label>
              <span className="AlertModal__label">Name</span>
              <Input className="AlertModal__input" value={name} onChange={this.update('name')}/>
            </label>
          </div>
          <div className="AlertModal__inputGroup">
            <label>
              <span className="AlertModal__label">Send Email to</span>
              <Input className="AlertModal__input" value={email} onChange={this.update('email')} />
            </label>
          </div>
          <div className="AlertModal__inputGroup">
            <label>
              <span className="AlertModal__label">when Report</span>
              <Select className="AlertModal__input" value={reportId} onChange={this.update('reportId')}>
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
                <Select value={thresholdOperator} onChange={this.update('thresholdOperator')}>
                  <Select.Option value='>'>above</Select.Option>
                  <Select.Option value='<'>below</Select.Option>
                </Select>
                <Input className="AlertModal__input" value={threshold} onChange={this.update('threshold')} />
              </div>
            </label>
          </div>
        </div>
        <div className="AlertModal__inputGroup">
          <label>
            <span className="AlertModal__label">Check Report every</span>
            <div className="AlertModal__combinedInput">
              <Input className="AlertModal__input" value={checkInterval.value} onChange={this.update('checkInterval', 'value')}/>
              <Select value={checkInterval.unit} onChange={this.update('checkInterval', 'unit')}>
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
            <Input type='checkbox' checked={fixNotification} onChange={this.update('fixNotification')} />
            Send Fix Notification
          </label>
        </div>
        <div className="AlertModal__inputGroup">
          <label>
            <Input type='checkbox' checked={!!reminder} onChange={this.update('reminder')} />
            Send Reminder Mails
          </label>
          {reminder &&
            <div className="AlertModal__inputGroup">
              <label>
                <span className="AlertModal__label">every</span>
                <div className="AlertModal__combinedInput">
                  <Input className="AlertModal__input" value={reminder.value} onChange={this.update('reminder', 'value')} />
                  <Select value={reminder.unit} onChange={this.update('reminder', 'unit')}>
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
