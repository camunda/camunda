/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Viewer from 'bpmn-js/lib/NavigatedViewer';

import {
  Button,
  Modal,
  BPMNDiagram,
  TargetValueBadge,
  Message,
  LoadingIndicator,
  ClickBehavior,
} from 'components';
import {t} from 'translation';
import {isValidInput} from './service';
import NodesTable from './NodesTable';

import './NodeDuration.scss';

export default class NodeDuration extends React.Component {
  state = {
    focus: null,
    values: {},
    nodeNames: {},
    loading: false,
  };

  async componentDidMount() {
    this.setState({loading: true});
    const {values, nodeNames} = await this.constructValues();
    this.setState({
      focus: null,
      values,
      nodeNames,
      loading: false,
    });
  }

  constructValues = async () => {
    const viewer = new Viewer();
    await viewer.importXML(this.props.xml);
    const predefinedValues = this.props.filterData?.data || {};
    const values = {};
    const nodeNames = {};

    const set = new Set();
    viewer
      .get('elementRegistry')
      .filter((element) => element.businessObject.$instanceOf('bpmn:FlowNode'))
      .map((element) => element.businessObject)
      .forEach((element) => set.add(element));

    set.forEach((element) => {
      values[element.id] = copyObjectIfExistsAndStringifyValue(predefinedValues[element.id]);
      nodeNames[element.id] = element.name || element.id;
    });

    return {values, nodeNames};
  };

  cleanUpValues = () => {
    // this function removes all entries without value and converts values into numbers
    const values = {};

    Object.keys(this.state.values).forEach((key) => {
      const entry = this.state.values[key];
      if (entry && entry.value.trim()) {
        values[key] = {
          operator: entry.operator,
          value: parseFloat(entry.value),
          unit: entry.unit,
        };
      }
    });

    return values;
  };

  validChanges = () => {
    return this.hasSomethingChanged() && this.areAllFieldsNumbers();
  };

  hasSomethingChanged = () => {
    const prev = this.props.filterData?.data || {};
    const now = this.cleanUpValues();

    return JSON.stringify(prev) !== JSON.stringify(now);
  };

  areAllFieldsNumbers = () => {
    return Object.keys(this.state.values)
      .filter((key) => this.state.values[key])
      .every((key) => {
        const entry = this.state.values[key];
        const value = entry && entry.value;
        return isValidInput(value);
      });
  };

  updateFocus = (focus) => this.setState({focus});

  confirmModal = () => {
    const data = this.cleanUpValues();
    this.props.addFilter({
      type: 'flowNodeDuration',
      data,
    });
  };

  render() {
    const {loading, focus, nodeNames, values} = this.state;
    let errorMessage = !this.hasSomethingChanged() ? t('report.heatTarget.noChangeWarning') : '';
    if (!this.areAllFieldsNumbers()) {
      errorMessage += t('report.heatTarget.invalidValue');
    }

    const activeNodes = Object.keys(this.cleanUpValues());
    const empty = activeNodes.length === 0;
    if (focus) {
      activeNodes.push(focus);
    }

    return (
      <Modal size="max" open onClose={this.props.close} className="NodeDuration">
        <Modal.Header>{t('common.filter.types.flowNodeDuration')} </Modal.Header>
        <Modal.Content className="content-container">
          {loading && <LoadingIndicator />}
          <div className="diagram-container">
            <BPMNDiagram xml={this.props.xml}>
              <ClickBehavior onClick={({id}) => this.updateFocus(id)} selectedNodes={activeNodes} />
              <TargetValueBadge values={values} />
            </BPMNDiagram>
          </div>
          {!loading && (
            <NodesTable
              focus={focus}
              updateFocus={this.updateFocus}
              nodeNames={nodeNames}
              values={values}
              onChange={(values) => this.setState({values})}
            />
          )}
          {!this.validChanges() && !loading && <Message error>{errorMessage}</Message>}
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button main primary onClick={this.confirmModal} disabled={empty || !this.validChanges()}>
            {this.props.filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

function copyObjectIfExistsAndStringifyValue(obj) {
  if (obj) {
    return {
      ...obj,
      value: '' + obj.value,
    };
  }
  return obj;
}
