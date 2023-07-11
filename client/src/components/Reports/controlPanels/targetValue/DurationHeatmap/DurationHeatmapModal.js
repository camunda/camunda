/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import classnames from 'classnames';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {Button} from '@carbon/react';

import {
  Modal,
  BPMNDiagram,
  Table,
  Input,
  Select,
  TargetValueBadge,
  Message,
  LoadingIndicator,
  ClickBehavior,
} from 'components';
import {formatters, numberParser, getReportResult} from 'services';

import './DurationHeatmapModal.scss';
import {t} from 'translation';

export default class DurationHeatmapModal extends React.Component {
  constructor(props) {
    super(props);

    this.inputRefs = {};

    this.state = {
      focus: null,
      values: {},
      nodeNames: {},
      loading: false,
    };
  }

  getConfig = () => this.props.report.data.configuration.heatmapTargetValue;

  confirmModal = () => {
    this.props.onConfirm(this.cleanUpValues());
  };

  cleanUpValues = () => {
    // this function removes all entries without value and converts values into numbers
    const values = {};

    Object.keys(this.state.values).forEach((key) => {
      const entry = this.state.values[key];
      if (entry && entry.value.trim()) {
        values[key] = {
          value: parseFloat(entry.value),
          unit: entry.unit,
        };
      }
    });

    return values;
  };

  constructValues = async () => {
    const {data} = this.props.report;
    const viewer = new Viewer();
    await viewer.importXML(data.configuration.xml);
    const predefinedValues = this.getConfig().values || {};
    const values = {};
    const nodeNames = {};

    const set = new Set();
    viewer
      .get('elementRegistry')
      .filter((element) => element.businessObject.$instanceOf('bpmn:' + this.getNodeType()))
      .map((element) => element.businessObject)
      .forEach((element) => set.add(element));
    set.forEach((element) => {
      values[element.id] = this.copyObjectIfExistsAndStringifyValue(predefinedValues[element.id]);
      nodeNames[element.id] = element.name || element.id;
    });

    return {values, nodeNames};
  };

  copyObjectIfExistsAndStringifyValue = (obj) => {
    if (obj) {
      return {
        ...obj,
        value: '' + obj.value,
      };
    }
    return obj;
  };

  setTarget = (type, id) => (value) => {
    if (this.state.values[id]) {
      this.setState(
        update(this.state, {
          values: {
            [id]: {
              [type]: {$set: value},
            },
          },
        })
      );
    } else {
      this.setState(
        update(this.state, {
          values: {
            [id]: {
              $set: {
                value: '0',
                unit: 'hours',
                [type]: value,
              },
            },
          },
        })
      );
    }
  };

  storeInputReferenceFor = (id) => (input) => {
    this.inputRefs[id] = input;
  };

  constructTableBody = () => {
    const reportResults = getReportResult(this.props.report).data;
    const resultObj = Array.isArray(reportResults)
      ? formatters.objectifyResult(getReportResult(this.props.report).data)
      : {};

    return Object.keys(this.state.values).map((id) => {
      const settings = this.state.values[id] || {value: '', unit: 'hours'};
      const resultEntry = resultObj[id];
      return [
        this.state.nodeNames[id],
        formatters.duration(resultEntry || 0),
        <>
          <div className="selection">
            <Input
              value={settings.value}
              type="number"
              ref={this.storeInputReferenceFor(id)}
              onChange={(evt) => this.setTarget('value', id)(evt.target.value)}
              onFocus={() => {
                this.updateFocus(id);
              }}
              onBlur={() => {
                this.updateFocus(null);
              }}
              isInvalid={!this.isValidInput(settings.value)}
            />
            <Select
              value={settings.unit}
              onChange={(value) => {
                this.setTarget('unit', id)(value);
                this.updateFocus(id);
              }}
            >
              <Select.Option value="millis">{t('common.unit.milli.label-plural')}</Select.Option>
              <Select.Option value="seconds">{t('common.unit.second.label-plural')}</Select.Option>
              <Select.Option value="minutes">{t('common.unit.minute.label-plural')}</Select.Option>
              <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
              <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
              <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
              <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
              <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
            </Select>
          </div>
        </>,
      ];
    });
  };

  validChanges = () => {
    return this.hasSomethingChanged() && this.areAllFieldsNumbers();
  };

  isValidInput = (value) => {
    return value.trim() === '' || numberParser.isPositiveNumber(value);
  };

  hasSomethingChanged = () => {
    const prev = this.getConfig().values || {};
    const now = this.cleanUpValues();

    return JSON.stringify(prev) !== JSON.stringify(now);
  };

  areAllFieldsNumbers = () => {
    return Object.keys(this.state.values)
      .filter((key) => this.state.values[key])
      .every((key) => {
        const entry = this.state.values[key];
        const value = entry && entry.value;

        return this.isValidInput(value);
      });
  };

  updateFocus = (focus) => this.setState({focus});

  async componentDidUpdate(prevProps, prevState) {
    if (this.state.focus && this.state.focus !== prevState.focus) {
      this.inputRefs[this.state.focus].focus();
      this.inputRefs[this.state.focus].select();
    }

    if (this.props.open !== prevProps.open) {
      if (this.props.open) {
        this.setState({loading: true});
        const {values, nodeNames} = await this.constructValues();
        this.setState({
          focus: null,
          values,
          nodeNames,
          loading: false,
        });
      } else {
        this.setState({
          focus: null,
          values: {},
          nodeNames: {},
          loading: false,
        });
      }
    }
  }

  getNodeType = () => {
    if (this.props.report.data.view.entity === 'userTask') {
      return 'UserTask';
    }
    return 'FlowNode';
  };

  render() {
    const {open, onClose, report} = this.props;
    const nodeType = this.getNodeType();

    return (
      <Modal
        size="lg"
        open={open}
        onClose={onClose}
        className={classnames('DurationHeatmapModal', 'type-' + nodeType)}
      >
        <Modal.Header>{t('report.heatTarget.title')} </Modal.Header>
        <Modal.Content className="content-container">
          {this.state.loading && <LoadingIndicator />}
          <div className="diagram-container">
            <BPMNDiagram xml={report.data.configuration.xml}>
              <ClickBehavior
                onClick={({id}) => this.updateFocus(id)}
                selectedNodes={this.state.focus ? [this.state.focus] : []}
                nodeTypes={[nodeType]}
              />
              <TargetValueBadge values={this.state.values} />
            </BPMNDiagram>
          </div>
          {!this.state.loading && (
            <Table
              head={[
                t('report.heatTarget.table.activity'),
                t('report.heatTarget.table.value'),
                t('report.heatTarget.table.target'),
              ]}
              body={this.constructTableBody()}
              disablePagination
            />
          )}
          {!this.areAllFieldsNumbers() && !this.state.loading && (
            <Message error>{t('report.heatTarget.invalidValue')}</Message>
          )}
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={this.confirmModal} className="confirm" disabled={!this.validChanges()}>
            {t('common.apply')}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }
}
