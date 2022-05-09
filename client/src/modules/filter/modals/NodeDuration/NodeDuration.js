/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {loadProcessDefinitionXml} from 'services';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import {isValidInput} from './service';
import NodesTable from './NodesTable';

import './NodeDuration.scss';

export class NodeDuration extends React.Component {
  state = {
    focus: null,
    values: {},
    nodeNames: {},
    loading: false,
    applyTo: null,
    xml: null,
  };

  async componentDidMount() {
    this.setState({loading: true});

    const validDefinitions = this.props.definitions.filter(
      (definition) => definition.versions.length && definition.tenantIds.length
    );

    const applyTo =
      validDefinitions.find(({identifier}) => this.props.filterData?.appliedTo[0] === identifier) ||
      validDefinitions[0];

    const {values, nodeNames, xml} = await this.constructValues(
      applyTo,
      this.props.filterData?.data
    );
    this.setState({
      focus: null,
      values,
      nodeNames,
      loading: false,
      applyTo,
      xml,
    });
  }

  async componentDidUpdate(prevProps, prevState) {
    if (prevState.applyTo && prevState.applyTo !== this.state.applyTo) {
      this.setState({loading: true});

      const {values, nodeNames, xml} = await this.constructValues(this.state.applyTo);
      this.setState({
        focus: null,
        values,
        nodeNames,
        loading: false,
        xml,
      });
    }
  }

  constructValues = ({key, versions, tenantIds}, predefinedValues = {}) => {
    return this.props.mightFail(
      loadProcessDefinitionXml(key, versions[0], tenantIds[0]),
      async (xml) => {
        const viewer = new Viewer();
        await viewer.importXML(xml);
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

        return {values, nodeNames, xml};
      },
      showError
    );
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
      appliedTo: [this.state.applyTo.identifier],
    });
  };

  render() {
    const {close, filterData, definitions} = this.props;
    const {loading, focus, nodeNames, values, applyTo, xml} = this.state;

    const activeNodes = Object.keys(this.cleanUpValues());
    const empty = activeNodes.length === 0;
    if (focus) {
      activeNodes.push(focus);
    }

    return (
      <Modal size="max" open onClose={close} className="NodeDuration">
        <Modal.Header>{t('common.filter.types.flowNodeDuration')} </Modal.Header>
        <Modal.Content className="content-container">
          <FilterSingleDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={(applyTo) => this.setState({applyTo})}
          />
          {loading && <LoadingIndicator />}
          {!loading && (
            <>
              <div className="diagram-container">
                <BPMNDiagram xml={xml}>
                  <ClickBehavior
                    onClick={({id}) => this.updateFocus(id)}
                    selectedNodes={activeNodes}
                  />
                  <TargetValueBadge values={values} />
                </BPMNDiagram>
              </div>
              <NodesTable
                focus={focus}
                updateFocus={this.updateFocus}
                nodeNames={nodeNames}
                values={values}
                onChange={(values) => this.setState({values})}
              />
            </>
          )}
          {!this.areAllFieldsNumbers() && !loading && (
            <Message error>{t('report.heatTarget.invalidValue')}</Message>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button main primary onClick={this.confirmModal} disabled={empty || !this.validChanges()}>
            {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
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

export default withErrorHandling(NodeDuration);
