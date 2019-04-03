/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import Viewer from 'bpmn-js/lib/NavigatedViewer';

import TargetValueDiagramBehavior from './TargetValueDiagramBehavior';

import {
  Button,
  Modal,
  BPMNDiagram,
  Table,
  Input,
  Select,
  TargetValueBadge,
  ErrorMessage,
  LoadingIndicator
} from 'components';
import {formatters, numberParser} from 'services';

import './DurationHeatmapModal.scss';

export default class DurationHeatmapModal extends React.Component {
  constructor(props) {
    super(props);

    this.inputRefs = {};

    this.state = {
      focus: null,
      values: {},
      nodeNames: {},
      loading: false
    };
  }

  getConfig = () => this.props.report.data.configuration.heatmapTargetValue;

  confirmModal = () => {
    this.props.onConfirm(this.cleanUpValues());
  };

  cleanUpValues = () => {
    // this function removes all entries without value and converts values into numbers
    const values = {};

    Object.keys(this.state.values).forEach(key => {
      const entry = this.state.values[key];
      if (entry && entry.value.trim()) {
        values[key] = {
          value: parseFloat(entry.value),
          unit: entry.unit
        };
      }
    });

    return values;
  };

  constructValues = () => {
    const {data} = this.props.report;
    return new Promise(resolve => {
      const viewer = new Viewer();
      viewer.importXML(data.configuration.xml, () => {
        const predefinedValues = this.getConfig().values || {};
        const values = {};
        const nodeNames = {};

        const set = new Set();
        viewer
          .get('elementRegistry')
          .filter(element => element.businessObject.$instanceOf('bpmn:' + this.getNodeType()))
          .map(element => element.businessObject)
          .forEach(element => set.add(element));
        set.forEach(element => {
          values[element.id] = this.copyObjectIfExistsAndStringifyValue(
            predefinedValues[element.id]
          );
          nodeNames[element.id] = element.name || element.id;
        });

        resolve({values, nodeNames});
      });
    });
  };

  copyObjectIfExistsAndStringifyValue = obj => {
    if (obj) {
      return {
        ...obj,
        value: '' + obj.value
      };
    }
    return obj;
  };

  setTarget = (type, id) => ({target: {value}}) => {
    if (this.state.values[id]) {
      this.setState(
        update(this.state, {
          values: {
            [id]: {
              [type]: {$set: value}
            }
          }
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
                [type]: value
              }
            }
          }
        })
      );
    }
  };

  storeInputReferenceFor = id => input => {
    this.inputRefs[id] = input;
  };

  constructTableBody = () => {
    return Object.keys(this.state.values).map(id => {
      const settings = this.state.values[id] || {value: '', unit: 'hours'};
      const resultEntry = this.props.report.result.data[id];
      return [
        this.state.nodeNames[id],
        formatters.duration(
          (resultEntry && resultEntry[this.props.report.data.configuration.aggregationType]) || 0
        ),
        <React.Fragment>
          <div className="DurationHeatmapModal__selection">
            <Input
              value={settings.value}
              type="number"
              ref={this.storeInputReferenceFor(id)}
              onChange={this.setTarget('value', id)}
              onFocus={() => {
                this.updateFocus(id);
              }}
              onBlur={() => {
                this.updateFocus(null);
              }}
              className="DurationHeatmapModal__selection--input"
              isInvalid={!this.isValidInput(settings.value)}
            />
            <Select
              value={settings.unit}
              onChange={evt => {
                this.setTarget('unit', id)(evt);
                this.updateFocus(id);
              }}
              className="DurationHeatmapModal__selection--select"
            >
              <Select.Option value="millis">Milliseconds</Select.Option>
              <Select.Option value="seconds">Seconds</Select.Option>
              <Select.Option value="minutes">Minutes</Select.Option>
              <Select.Option value="hours">Hours</Select.Option>
              <Select.Option value="days">Days</Select.Option>
              <Select.Option value="weeks">Weeks</Select.Option>
              <Select.Option value="months">Months</Select.Option>
              <Select.Option value="years">Years</Select.Option>
            </Select>
          </div>
        </React.Fragment>
      ];
    });
  };

  validChanges = () => {
    return this.hasSomethingChanged() && this.areAllFieldsNumbers();
  };

  isValidInput = value => {
    return value.trim() === '' || numberParser.isPositiveNumber(value);
  };

  hasSomethingChanged = () => {
    const prev = this.getConfig().values || {};
    const now = this.cleanUpValues();

    return JSON.stringify(prev) !== JSON.stringify(now);
  };

  areAllFieldsNumbers = () => {
    return Object.keys(this.state.values)
      .filter(key => this.state.values[key])
      .every(key => {
        const entry = this.state.values[key];
        const value = entry && entry.value;

        return this.isValidInput(value);
      });
  };

  updateFocus = focus => this.setState({focus});

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
          loading: false
        });
      } else {
        this.setState({
          values: {},
          nodeNames: {},
          loading: false
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
    let errorMessage = !this.hasSomethingChanged() ? 'Please change at least one value' : '';
    if (!this.areAllFieldsNumbers()) errorMessage += 'All fields should have a numeric value';
    return (
      <Modal size="max" open={open} onClose={onClose} className="DurationHeatmapModal__Modal">
        <Modal.Header>Target Value Comparison </Modal.Header>
        <Modal.Content className="DurationHeatmapModal__modal-content-container">
          {this.state.loading && <LoadingIndicator />}
          <div className="DurationHeatmapModal__DiagramContainer">
            <BPMNDiagram xml={report.data.configuration.xml}>
              <TargetValueDiagramBehavior
                onClick={this.updateFocus}
                focus={this.state.focus}
                nodeType={this.getNodeType()}
              />
              <TargetValueBadge values={this.state.values} />
            </BPMNDiagram>
          </div>
          {!this.state.loading && (
            <Table
              head={['Activity', 'Actual Value', 'Target Value']}
              body={this.constructTableBody()}
              foot={[]}
              className="DurationHeatmapModal__Table"
              disablePagination
            />
          )}
          {!this.validChanges() && !this.state.loading && (
            <ErrorMessage className="DurationHeatmapModal__warning">{errorMessage}</ErrorMessage>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            onClick={this.confirmModal}
            disabled={!this.validChanges()}
          >
            Apply
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
