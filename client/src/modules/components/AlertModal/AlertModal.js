/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import {
  Button,
  Checkbox,
  ComboBox,
  TextInput,
  Form,
  Stack,
  ActionableNotification,
  Grid,
  Column,
} from '@carbon/react';

import {Modal, CarbonSelect} from 'components';
import {formatters, evaluateReport, getReportResult} from 'services';
import {isEmailEnabled} from 'config';
import {t} from 'translation';
import {withDocs, withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {numberParser} from 'services';

import ThresholdInput from './ThresholdInput';
import MultiEmailInput from './MultiEmailInput';

import './AlertModal.scss';

const newAlert = {
  emails: [],
  reportId: '',
  thresholdOperator: '>',
  threshold: '100',
  checkInterval: {
    value: '10',
    unit: 'minutes',
  },
  reminder: null,
  fixNotification: false,
  webhook: undefined,
};

export class AlertModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      ...newAlert,
      reportId: '',
      name: t('alert.newAlert'),
      inactive: false,
      invalid: false,
      validEmails: true,
      report: null,
    };
  }

  componentDidMount = async () => {
    if (this.isDefined(this.props.initialAlert)) {
      this.loadAlert();
    } else if (this.props.initialReport) {
      this.updateReport(this.props.initialReport);
    }

    this.setState({
      emailNotificationIsEnabled: await isEmailEnabled(),
    });
  };

  isDefined = (alert) => alert && Object.keys(alert).length;

  loadAlert() {
    const alert = this.props.initialAlert;
    if (!this.isDefined(alert)) {
      this.setState({...newAlert, name: t('alert.newAlert')});
    }

    this.setState({
      ...alert,
      inactive:
        alert.webhook && !alert.emails?.length && !this.props.webhooks?.includes(alert.webhook),
      threshold:
        this.getReportMeasure(alert.reportId) === 'duration'
          ? formatters.convertDurationToObject(alert.threshold)
          : alert.threshold.toString(),
      checkInterval: {
        value: alert.checkInterval.value.toString(),
        unit: alert.checkInterval.unit,
      },
      reminder: alert.reminder
        ? {
            value: alert.reminder.value.toString(),
            unit: alert.reminder.unit,
          }
        : null,
    });
    this.loadReport(alert.reportId);
  }

  loadReport = (id) => {
    this.props.mightFail(
      evaluateReport(id),
      (report) =>
        this.setState({
          report,
        }),
      showError
    );
  };

  updateReminder = ({target: {checked}}) => {
    if (checked) {
      this.setState({
        reminder: {
          value: '2',
          unit: 'hours',
        },
      });
    } else {
      this.setState({
        reminder: null,
      });
    }
  };

  setInvalid = (isInvalid) => {
    if (this.state.invalid !== isInvalid) {
      this.setState({
        invalid: isInvalid,
      });
    }
  };

  confirm = () => {
    this.props.onConfirm({
      ...this.state,
      threshold: formatters.convertDurationToSingleNumber(this.state.threshold),
      emails: [...new Set(this.state.emails)],
    });
  };

  isInEditingMode = () => {
    return this.props.initialAlert && this.props.initialAlert.id;
  };

  isThresholdValid = () => {
    const reportMeasure = this.getReportMeasure(this.state.reportId);
    const isNonNegative = numberParser.isNonNegativeNumber(this.getThresholdValue());
    if (reportMeasure === 'percentage') {
      return isNonNegative && Number(this.getThresholdValue()) <= 100;
    }

    return isNonNegative;
  };

  componentDidUpdate({initialAlert}) {
    const {name, webhook, emails, reportId, checkInterval, reminder, validEmails} = this.state;

    if (this.props.initialAlert !== initialAlert) {
      this.loadAlert();
    }
    if (!name.trim()) {
      this.setInvalid(true);
      return;
    }
    if (emails.length && !validEmails) {
      this.setInvalid(true);
      return;
    }
    if (!emails?.length && !webhook) {
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
    if (!numberParser.isPositiveInt(checkInterval.value)) {
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

  getReportMeasure = (reportId) => {
    const report = this.props.reports.find(({id}) => id === reportId);

    if (report) {
      return report.data.view.properties[0];
    }
  };

  getThresholdValue = () => {
    const {threshold} = this.state;
    return typeof threshold.value !== 'undefined' ? threshold.value : threshold;
  };

  updateReport = (id) => {
    const reportMeasure = this.getReportMeasure(id);
    const currentValue = this.getThresholdValue();

    this.setState({
      reportId: id,
      threshold: reportMeasure === 'duration' ? {value: currentValue, unit: 'days'} : currentValue,
    });
    this.loadReport(id);
  };

  updateWebhook = (webhook) => {
    this.setState({webhook});
  };

  render() {
    const {
      name,
      emails,
      reportId,
      thresholdOperator,
      threshold,
      checkInterval,
      reminder,
      fixNotification,
      emailNotificationIsEnabled,
      inactive,
      invalid,
      webhook,
      validEmails,
      report,
    } = this.state;

    const {reports, webhooks, onClose, onRemove, disabled} = this.props;
    const selectedReport = reports.find((report) => report.id === reportId) || {};
    const reportMeasure = this.getReportMeasure(reportId);

    return (
      <Modal open onClose={onClose} className="AlertModal">
        <Modal.Header>
          {this.isInEditingMode() ? t('alert.edit') : t('alert.createNew')}
        </Modal.Header>
        <Modal.Content>
          <Grid>
            <Column sm={4} md={8} lg={16}>
              <Form autoComplete="off">
                <Stack gap={6}>
                  {!emailNotificationIsEnabled && (
                    <ActionableNotification inline kind="warning" hideCloseButton>
                      {t('alert.emailWarning', {
                        docsLink:
                          this.props.docsLink +
                          'self-managed/optimize-deployment/configuration/system-configuration/#email',
                      })}
                    </ActionableNotification>
                  )}
                  {inactive && (
                    <ActionableNotification inline kind="warning" hideCloseButton>
                      {t('alert.inactiveStatus')}
                      <br />
                      {t('alert.activateInfo')}
                    </ActionableNotification>
                  )}
                  <TextInput
                    id="alertName"
                    labelText={t('alert.form.name')}
                    value={name}
                    onChange={({target: {value}}) => this.setState({name: value})}
                    autoComplete="off"
                  />
                  <ComboBox
                    id="report"
                    size="sm"
                    items={reports}
                    selectedItem={selectedReport}
                    itemToString={(item) => item.name || item.id}
                    disabled={!!this.props.initialReport || !reports?.length}
                    placeholder={t('alert.form.reportPlaceholder')}
                    titleText={t('alert.form.report')}
                    onChange={({selectedItem}) => {
                      if (selectedItem) {
                        this.updateReport(selectedItem.id);
                      }
                    }}
                    helperText={
                      report
                        ? t('alert.form.value', {
                            value: reportId === report.id ? getReportValue(report) : '...',
                          })
                        : t('alert.form.reportInfo')
                    }
                  />
                  <Stack gap={6} orientation="horizontal">
                    <CarbonSelect
                      id="threshold"
                      labelText={t('alert.form.threshold')}
                      value={thresholdOperator}
                      onChange={(value) => this.setState({thresholdOperator: value})}
                    >
                      <CarbonSelect.Option value=">" label={t('common.above')} />
                      <CarbonSelect.Option value="<" label={t('common.below')} />
                    </CarbonSelect>
                    <ThresholdInput
                      className="labelHidden"
                      value={threshold}
                      onChange={(threshold) => this.setState({threshold})}
                      type={reportMeasure}
                      labelText={t('common.value')}
                      invalid={!this.isThresholdValid()}
                      invalidText={
                        reportMeasure === 'percentage'
                          ? t('common.errors.percentage')
                          : t('common.errors.number')
                      }
                    />
                  </Stack>
                  <Stack gap={6}>
                    <Stack gap={6} orientation="horizontal">
                      <TextInput
                        id="frequency"
                        size="sm"
                        labelText={t('alert.form.frequency')}
                        value={checkInterval.value}
                        onChange={({target: {value}}) =>
                          this.setState(update(this.state, {checkInterval: {value: {$set: value}}}))
                        }
                        maxLength="8"
                        invalid={!numberParser.isPositiveInt(checkInterval.value)}
                        invalidText={t('common.errors.positiveInt')}
                      />
                      <CarbonSelect
                        id="frequencyUnits"
                        labelText={t('common.units')}
                        className="labelHidden"
                        value={checkInterval.unit}
                        onChange={(value) =>
                          this.setState(update(this.state, {checkInterval: {unit: {$set: value}}}))
                        }
                      >
                        <CarbonSelect.Option
                          value="seconds"
                          label={t('common.unit.second.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="minutes"
                          label={t('common.unit.minute.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="hours"
                          label={t('common.unit.hour.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="days"
                          label={t('common.unit.day.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="weeks"
                          label={t('common.unit.week.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="months"
                          label={t('common.unit.month.label-plural')}
                        />
                      </CarbonSelect>
                    </Stack>
                  </Stack>
                  <MultiEmailInput
                    titleText={t('alert.form.email')}
                    placeholder={t('alert.form.emailPlaceholder')}
                    emails={emails}
                    onChange={(emails, validEmails) => this.setState({emails, validEmails})}
                    helperText={t('alert.form.emailThreshold')}
                    invalid={!validEmails}
                    invalidText={t('alert.form.invalidEmail')}
                  />
                  {webhooks?.length > 0 && (
                    <Stack gap={6} orientation="horizontal">
                      <ComboBox
                        id="webhooks"
                        key={webhook}
                        size="sm"
                        items={webhooks}
                        selectedItem={webhook}
                        titleText={t('alert.form.webhook')}
                        placeholder={t('alert.form.webookPlaceholder')}
                        onChange={({selectedItem}) => this.updateWebhook(selectedItem || undefined)}
                      />
                      <Button
                        size="sm"
                        disabled={!webhook}
                        kind="secondary"
                        onClick={() => this.setState({webhook: undefined})}
                        className="reset"
                      >
                        {t('common.reset')}
                      </Button>
                    </Stack>
                  )}
                  <Checkbox
                    id="sendNotification"
                    labelText={t('alert.form.sendNotification')}
                    checked={fixNotification}
                    onChange={({target: {checked}}) => this.setState({fixNotification: checked})}
                  />
                  <Checkbox
                    id="sendReminder"
                    labelText={t('alert.form.reminder')}
                    checked={!!reminder}
                    onChange={this.updateReminder}
                  />
                  {reminder && (
                    <Stack gap={6} orientation="horizontal">
                      <TextInput
                        id="reminderFrequency"
                        size="sm"
                        labelText={t('alert.form.reminderFrequency')}
                        value={reminder.value}
                        onChange={({target: {value}}) =>
                          this.setState(update(this.state, {reminder: {value: {$set: value}}}))
                        }
                        maxLength="8"
                      />
                      <CarbonSelect
                        id="reminderFrequencyUnits"
                        labelText={t('common.units')}
                        className="labelHidden"
                        value={reminder.unit}
                        onChange={(value) =>
                          this.setState(update(this.state, {reminder: {unit: {$set: value}}}))
                        }
                      >
                        <CarbonSelect.Option
                          value="minutes"
                          label={t('common.unit.minute.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="hours"
                          label={t('common.unit.hour.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="days"
                          label={t('common.unit.day.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="weeks"
                          label={t('common.unit.week.label-plural')}
                        />
                        <CarbonSelect.Option
                          value="months"
                          label={t('common.unit.month.label-plural')}
                        />
                      </CarbonSelect>
                    </Stack>
                  )}
                </Stack>
              </Form>
            </Column>
          </Grid>
        </Modal.Content>
        <Modal.Footer>
          {onRemove && (
            <Button kind="danger--ghost" className="deleteAlertButton" onClick={onRemove}>
              {t('common.delete')}
            </Button>
          )}
          <Button className="cancel" kind="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button className="confirm" onClick={this.confirm} disabled={invalid || disabled}>
            {this.isInEditingMode() ? t('alert.apply') : t('alert.create')}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }
}

function getReportValue(report) {
  const reportType = report?.data?.view?.properties?.[0];
  return formatters[reportType](getReportResult(report).data);
}

export default withErrorHandling(withDocs(AlertModal));
