/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import {
  Modal,
  Message,
  Button,
  Labeled,
  Input,
  LabeledInput,
  Select,
  Typeahead,
  MessageBox,
  Form
} from 'components';
import {formatters, isDurationReport} from 'services';
import {isEmailEnabled, getOptimizeVersion} from 'config';

import ThresholdInput from './ThresholdInput';

import {t} from 'translation';

const newAlert = {
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

    this.state = {
      ...newAlert,
      name: t('alert.newAlert'),
      invalid: false
    };
  }

  componentDidMount = async () => {
    if (this.isDefined(this.props.initialAlert)) {
      this.loadAlert();
    }

    const version = (await getOptimizeVersion()).split('.');
    version.length = 2;

    this.setState({
      emailNotificationIsEnabled: await isEmailEnabled(),
      optimizeVersion: version.join('.')
    });
  };

  isDefined = alert => alert && Object.keys(alert).length;

  loadAlert() {
    const alert = this.props.initialAlert;
    if (!this.isDefined(alert)) {
      this.setState({...newAlert, name: t('alert.newAlert')});
    }

    this.setState({
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
    });
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
    return this.props.initialAlert && this.props.initialAlert.id;
  };

  isThresholdValid = () => {
    const value = this.getThresholdValue();
    return value.trim() && !isNaN(value);
  };

  componentDidUpdate({initialAlert}) {
    const {name, email, reportId, checkInterval, reminder} = this.state;

    if (this.props.initialAlert !== initialAlert) {
      this.loadAlert();
    }
    if (!name.trim()) {
      this.setInvalid(true);
      return;
    }
    if (!email.match(/^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/)) {
      // taken from https://www.w3.org/TR/2012/WD-html-markup-20120320/input.email.html#input.email.attrs.value.single
      this.setInvalid(true);
      return;
    }
    if (!reportId) {
      this.setInvalid(true);
      return;
    }
    if (!this.isThresholdValid()) {
      this.setInvalid(true);
      return;
    }
    if (
      !checkInterval.value.trim() ||
      isNaN(checkInterval.value.trim()) ||
      !(checkInterval.value > 0)
    ) {
      this.setInvalid(true);
      return;
    }
    if (
      reminder !== null &&
      (!reminder.value.trim() || isNaN(reminder.value.trim()) || !reminder.value > 0)
    ) {
      this.setInvalid(true);
      return;
    }
    this.setInvalid(false);
  }

  getReportType = reportId => {
    const report = this.props.reports.find(({id}) => id === reportId);

    if (report) {
      if (isDurationReport(report)) {
        return 'duration';
      }
      return report.data.view.property;
    }
  };

  getThresholdValue = () => {
    const {threshold} = this.state;
    return typeof threshold.value !== 'undefined' ? threshold.value : threshold;
  };

  updateReport = id => {
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
      invalid,
      optimizeVersion
    } = this.state;

    const {reports, onClose} = this.props;

    const docsLink = `https://docs.camunda.org/optimize/${optimizeVersion}/technical-guide/setup/configuration/#email`;
    const selectedReport = reports.find(report => report.id === reportId) || {};
    return (
      <Modal open onClose={onClose} className="AlertModal">
        <Modal.Header>
          {this.isInEditingMode() ? t('alert.edit') : t('alert.createNew')}
        </Modal.Header>
        <Modal.Content>
          <Form horizontal>
            {!emailNotificationIsEnabled && (
              <MessageBox
                type="warning"
                dangerouslySetInnerHTML={{__html: t('alert.emailWarning', {docsLink})}}
              />
            )}
            <Form.Group>
              <LabeledInput
                label={t('alert.form.name')}
                value={name}
                onChange={({target: {value}}) => this.setState({name: value})}
                autoComplete="off"
              />
            </Form.Group>
            <Form.Group>
              <Labeled label={t('alert.form.report')}>
                <Typeahead
                  initialValue={selectedReport.id}
                  placeholder={t('alert.form.reportPlaceholder')}
                  onChange={this.updateReport}
                  noValuesMessage={t('alert.form.noReports')}
                >
                  {reports.map(({id, name}) => (
                    <Typeahead.Option key={id} value={id}>
                      {name}
                    </Typeahead.Option>
                  ))}
                </Typeahead>
              </Labeled>
              <Message>{t('alert.form.reportInfo')}</Message>
            </Form.Group>
            <Form.Group>
              <span>{t('alert.form.threshold')}</span>
              <Form.InputGroup>
                <Select
                  value={thresholdOperator}
                  onChange={value => this.setState({thresholdOperator: value})}
                >
                  <Select.Option value=">">{t('common.above')}</Select.Option>
                  <Select.Option value="<">{t('common.below')}</Select.Option>
                </Select>
                <ThresholdInput
                  value={threshold}
                  onChange={threshold => this.setState({threshold})}
                  type={this.getReportType(reportId)}
                />
              </Form.InputGroup>
            </Form.Group>
            <Form.Group>
              <Labeled label={t('alert.form.frequency')}>
                <Form.InputGroup>
                  <Input
                    value={checkInterval.value}
                    onChange={({target: {value}}) =>
                      this.setState(update(this.state, {checkInterval: {value: {$set: value}}}))
                    }
                  />
                  <Select
                    value={checkInterval.unit}
                    onChange={value =>
                      this.setState(update(this.state, {checkInterval: {unit: {$set: value}}}))
                    }
                  >
                    <Select.Option value="seconds">
                      {t('common.unit.second.label-plural')}
                    </Select.Option>
                    <Select.Option value="minutes">
                      {t('common.unit.minute.label-plural')}
                    </Select.Option>
                    <Select.Option value="hours">
                      {t('common.unit.hour.label-plural')}
                    </Select.Option>
                    <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
                    <Select.Option value="weeks">
                      {t('common.unit.week.label-plural')}
                    </Select.Option>
                    <Select.Option value="months">
                      {t('common.unit.month.label-plural')}
                    </Select.Option>
                  </Select>
                </Form.InputGroup>
              </Labeled>
            </Form.Group>
            <Form.Group>
              <LabeledInput
                label={t('alert.form.email')}
                placeholder={t('alert.form.emailPlaceholder')}
                value={email}
                onChange={({target: {value}}) => this.setState({email: value})}
              />
            </Form.Group>
            <Form.Group noSpacing className="notifications">
              <LabeledInput
                label={t('alert.form.sendNotification')}
                type="checkbox"
                checked={fixNotification}
                onChange={({target: {checked}}) => this.setState({fixNotification: checked})}
              />
              <LabeledInput
                label={t('alert.form.reminder')}
                type="checkbox"
                checked={!!reminder}
                onChange={this.updateReminder}
              />
            </Form.Group>
            {reminder && (
              <Form.Group noSpacing>
                <Labeled label={t('alert.form.reminderFrequency')}>
                  <Form.InputGroup>
                    <Input
                      value={reminder.value}
                      onChange={({target: {value}}) =>
                        this.setState(update(this.state, {reminder: {value: {$set: value}}}))
                      }
                    />
                    <Select
                      value={reminder.unit}
                      onChange={value =>
                        this.setState(update(this.state, {reminder: {unit: {$set: value}}}))
                      }
                    >
                      <Select.Option value="minutes">
                        {t('common.unit.minute.label-plural')}
                      </Select.Option>
                      <Select.Option value="hours">
                        {t('common.unit.hour.label-plural')}
                      </Select.Option>
                      <Select.Option value="days">
                        {t('common.unit.day.label-plural')}
                      </Select.Option>
                      <Select.Option value="weeks">
                        {t('common.unit.week.label-plural')}
                      </Select.Option>
                      <Select.Option value="months">
                        {t('common.unit.month.label-plural')}
                      </Select.Option>
                    </Select>
                  </Form.InputGroup>
                </Labeled>
              </Form.Group>
            )}
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" color="blue" onClick={this.confirm} disabled={invalid}>
            {this.isInEditingMode() ? t('alert.apply') : t('alert.create')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
