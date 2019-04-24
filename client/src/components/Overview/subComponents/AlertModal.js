/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import {Modal, Button, Labeled, Input, LabeledInput, Select, Typeahead, Message} from 'components';
import {emailNotificationIsEnabled} from './service';
import {getOptimizeVersion} from 'services';

import ThresholdInput from './ThresholdInput';

import './AlertModal.scss';

import {formatters, isDurationReport} from 'services';

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

export default function AlertModal(reports) {
  return class AlertModal extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        ...newAlert,
        invalid: false
      };
    }

    componentDidMount = async () => {
      const alert = this.props.entity;
      if (alert && Object.keys(alert).length) {
        this.updateAlert();
      }

      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;

      this.setState({
        emailNotificationIsEnabled: await emailNotificationIsEnabled(),
        optimizeVersion: version.join('.')
      });
    };

    updateAlert() {
      const alert = this.props.entity;

      this.setState(
        (alert &&
          alert.id && {
            ...alert,
            threshold:
              this.getReportType(alert.reportId) === 'duration'
                ? formatters.convertDurationToObject(alert.threshold)
                : alert.threshold.toString(),
            checkInterval: {
              value: alert.checkInterval.value.toString(),
              unit: alert.checkInterval.unit
            },
            reminder: alert.reminder
              ? {
                  value: alert.reminder.value.toString(),
                  unit: alert.reminder.unit
                }
              : null
          }) ||
          newAlert
      );
    }

    updateReminder = ({target: {checked}}) => {
      if (checked) {
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
    };

    setInvalid = isInvalid => {
      if (this.state.invalid !== isInvalid) {
        this.setState({
          invalid: isInvalid
        });
      }
    };

    confirm = () => {
      this.props.onConfirm({
        ...this.state,
        threshold: formatters.convertDurationToSingleNumber(this.state.threshold)
      });
    };

    isInEditingMode = () => {
      return this.props.entity && this.props.entity.id;
    };

    isThresholdValid = () => {
      const value = this.getThresholdValue();
      return value.trim() && !isNaN(value);
    };

    componentDidUpdate({entity}) {
      if (this.props.entity !== entity) {
        this.updateAlert();
      }
      if (!this.state.name.trim()) {
        this.setInvalid(true);
        return;
      }
      if (
        !this.state.email.match(
          /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/
        )
      ) {
        // taken from https://www.w3.org/TR/2012/WD-html-markup-20120320/input.email.html#input.email.attrs.value.single
        this.setInvalid(true);
        return;
      }
      if (!this.state.reportId) {
        this.setInvalid(true);
        return;
      }
      if (!this.isThresholdValid()) {
        this.setInvalid(true);
        return;
      }
      if (
        !this.state.checkInterval.value.trim() ||
        isNaN(this.state.checkInterval.value.trim()) ||
        !(this.state.checkInterval.value > 0)
      ) {
        this.setInvalid(true);
        return;
      }
      if (
        this.state.reminder !== null &&
        (!this.state.reminder.value.trim() ||
          isNaN(this.state.reminder.value.trim()) ||
          !this.state.reminder.value > 0)
      ) {
        this.setInvalid(true);
        return;
      }
      this.setInvalid(false);
    }

    getReportType = reportId => {
      const report = reports.find(({id}) => id === reportId);

      if (report) {
        if (isDurationReport(report)) {
          return 'duration';
        }
        return report.data.view.property;
      }
    };

    getThresholdValue = () =>
      typeof this.state.threshold.value !== 'undefined'
        ? this.state.threshold.value
        : this.state.threshold;

    updateReport = ({id}) => {
      const reportType = this.getReportType(id);
      const currentValue = this.getThresholdValue();

      this.setState({
        reportId: id,
        threshold: reportType === 'duration' ? {value: currentValue, unit: 'days'} : currentValue
      });
    };

    render() {
      const {
        name,
        email,
        reportId,
        thresholdOperator,
        threshold,
        checkInterval,
        reminder,
        fixNotification,
        emailNotificationIsEnabled,
        optimizeVersion
      } = this.state;
      return (
        <Modal open={this.props.entity} onClose={this.props.onClose}>
          <Modal.Header>{this.isInEditingMode() ? 'Edit Alert' : 'Add new Alert'}</Modal.Header>
          <Modal.Content>
            <div className="AlertModal__topSection">
              <div className="AlertModal__inputGroup">
                {!emailNotificationIsEnabled && (
                  <Message type="warning">
                    The email notification service is not configured. Optimize won't be able to
                    inform you about critical values. Please check out the{' '}
                    <a
                      href={`https://docs.camunda.org/optimize/${optimizeVersion}/technical-guide/setup/configuration/#email`}
                    >
                      Optimize documentation
                    </a>{' '}
                    on how to enable the notification service.
                  </Message>
                )}
                <LabeledInput
                  id="name-input"
                  className="AlertModal__input"
                  label="Name"
                  value={name}
                  onChange={({target: {value}}) => this.setState({name: value})}
                  autoComplete="off"
                />
              </div>
              <div className="AlertModal__inputGroup">
                <LabeledInput
                  id="email-input"
                  label="Send Email to"
                  className="AlertModal__input"
                  value={email}
                  onChange={({target: {value}}) => this.setState({email: value})}
                />
              </div>
              <div className="AlertModal__inputGroup">
                <Labeled label="when Report">
                  <Typeahead
                    initialValue={reports.find(report => report.id === reportId)}
                    placeholder="Select a Report"
                    values={reports}
                    onSelect={this.updateReport}
                    formatter={({name}) => name}
                  />
                </Labeled>
                <div className="AlertModal__report-selection-note">
                  Note: you can only create an alert for a report visualized as Number
                </div>
              </div>
              <div className="AlertModal__inputGroup">
                <div className="AlertModal__combinedInput">
                  <Labeled label="has a value">
                    <Select
                      value={thresholdOperator}
                      className="thresholdSelect"
                      onChange={({target: {value}}) => this.setState({thresholdOperator: value})}
                    >
                      <Select.Option value=">">above</Select.Option>
                      <Select.Option value="<">below</Select.Option>
                    </Select>
                  </Labeled>
                  <ThresholdInput
                    id="value-input"
                    value={threshold}
                    onChange={threshold => this.setState({threshold})}
                    type={this.getReportType(reportId)}
                  />
                </div>
              </div>
            </div>
            <div className="AlertModal__inputGroup">
              <div className="AlertModal__combinedInput">
                <LabeledInput
                  id="checkInterval-input"
                  label="Check Report every"
                  className="AlertModal__input"
                  value={checkInterval.value}
                  onChange={({target: {value}}) =>
                    this.setState(update(this.state, {checkInterval: {value: {$set: value}}}))
                  }
                />
                <Select
                  value={checkInterval.unit}
                  onChange={({target: {value}}) =>
                    this.setState(update(this.state, {checkInterval: {unit: {$set: value}}}))
                  }
                >
                  <Select.Option value="seconds">Seconds</Select.Option>
                  <Select.Option value="minutes">Minutes</Select.Option>
                  <Select.Option value="hours">Hours</Select.Option>
                  <Select.Option value="days">Days</Select.Option>
                  <Select.Option value="weeks">Weeks</Select.Option>
                  <Select.Option value="months">Months</Select.Option>
                </Select>
              </div>
            </div>
            <div className="AlertModal__inputGroup">
              <Input
                id="notification-checkbox"
                type="checkbox"
                checked={fixNotification}
                onChange={({target: {checked}}) => this.setState({fixNotification: checked})}
              />
              <label htmlFor="notification-checkbox">Send notification when resolved</label>
            </div>
            <div className="AlertModal__inputGroup">
              <Input
                id="reminder-checkbox"
                type="checkbox"
                checked={!!reminder}
                onChange={this.updateReminder}
              />
              <label htmlFor="reminder-checkbox">Send reminder notification</label>
              {reminder && (
                <div className="AlertModal__inputGroup">
                  <div className="AlertModal__combinedInput">
                    <LabeledInput
                      id="reminder-input"
                      label="every"
                      className="AlertModal__input"
                      value={reminder.value}
                      onChange={({target: {value}}) =>
                        this.setState(update(this.state, {reminder: {value: {$set: value}}}))
                      }
                    />
                    <Select
                      value={reminder.unit}
                      onChange={({target: {value}}) =>
                        this.setState(update(this.state, {reminder: {unit: {$set: value}}}))
                      }
                    >
                      <Select.Option value="minutes">Minutes</Select.Option>
                      <Select.Option value="hours">Hours</Select.Option>
                      <Select.Option value="days">Days</Select.Option>
                      <Select.Option value="weeks">Weeks</Select.Option>
                      <Select.Option value="months">Months</Select.Option>
                    </Select>
                  </div>
                </div>
              )}
            </div>
          </Modal.Content>
          <Modal.Actions>
            <Button onClick={this.props.onClose}>Cancel</Button>
            <Button
              type="primary"
              color="blue"
              onClick={this.confirm}
              disabled={this.state.invalid}
            >
              {this.isInEditingMode() ? 'Apply Changes' : 'Add Alert'}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  };
}
