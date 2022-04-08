/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import Viewer from 'bpmn-js/lib/NavigatedViewer';

import {Modal, Button, BPMNDiagram, ClickBehavior} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import './NodeSelection.scss';

export class NodeSelection extends React.Component {
  state = {
    allFlowNodes: [],
    selectedNodes: [],
    applyTo: null,
    xml: null,
  };

  componentDidMount() {
    const validDefinitions = this.props.definitions.filter(
      (definition) => definition.versions.length && definition.tenantIds.length
    );

    this.updateXml(
      validDefinitions.find(({identifier}) => this.props.filterData?.appliedTo[0] === identifier) ||
        validDefinitions[0],
      this.props.filterData
    );
  }

  async componentDidUpdate(prevProps, prevState) {
    if (prevState.applyTo && prevState.applyTo !== this.state.applyTo) {
      this.setState({selectedNodes: []});
      await this.updateXml(this.state.applyTo);
    }
  }

  updateXml = (applyTo, filterData) => {
    this.setState({xml: null});
    return this.props.mightFail(
      loadProcessDefinitionXml(applyTo.key, applyTo.versions[0], applyTo.tenantIds[0]),
      async (xml) => {
        const viewer = new Viewer();
        await viewer.importXML(xml);

        const flowNodes = new Set();
        viewer
          .get('elementRegistry')
          .filter((element) => element.businessObject.$instanceOf('bpmn:FlowNode'))
          .map((element) => element.businessObject)
          .forEach((element) => flowNodes.add(element.id));
        const allFlowNodes = Array.from(flowNodes);

        let preExistingValues;
        if (filterData?.data.values) {
          preExistingValues = allFlowNodes.filter((id) => !filterData?.data.values.includes(id));
        }

        this.setState({
          allFlowNodes,
          selectedNodes: preExistingValues || allFlowNodes,
          xml,
          applyTo,
        });
      },
      showError
    );
  };

  toggleNode = (toggledNode) => {
    this.setState(({selectedNodes}) => {
      if (selectedNodes.includes(toggledNode.id)) {
        return {selectedNodes: selectedNodes.filter((node) => node !== toggledNode.id)};
      } else {
        return {selectedNodes: selectedNodes.concat([toggledNode.id])};
      }
    });
  };

  createFilter = () => {
    const {allFlowNodes, selectedNodes, applyTo} = this.state;

    this.props.addFilter({
      type: 'executedFlowNodes',
      data: {operator: 'not in', values: allFlowNodes.filter((id) => !selectedNodes.includes(id))},
      appliedTo: [applyTo.identifier],
    });
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  isAllSelected = () => {
    return this.state.allFlowNodes.length === this.state.selectedNodes.length;
  };

  selectAll = () => {
    this.setState({
      selectedNodes: this.state.allFlowNodes,
    });
  };

  deselectAll = () => {
    this.setState({selectedNodes: []});
  };

  isValidSelection = () => {
    return this.isNodeSelected() && !this.isAllSelected();
  };

  render() {
    const {close, filterData, definitions} = this.props;
    const {applyTo, xml} = this.state;

    return (
      <Modal
        open
        onClose={close}
        onConfirm={this.isValidSelection() ? this.createFilter : undefined}
        className="NodeSelection"
        size="max"
      >
        <Modal.Header>{t('common.filter.types.flowNodeSelection')}</Modal.Header>
        <Modal.Content className="modalContent">
          <FilterSingleDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={(applyTo) => this.setState({applyTo})}
          />
          <div className="diagramActions">
            <Button disabled={false} onClick={this.selectAll}>
              {t('common.selectAll')}
            </Button>
            <Button disabled={!this.isNodeSelected()} onClick={this.deselectAll}>
              {t('common.deselectAll')}
            </Button>
          </div>
          {xml && (
            <div className="diagramContainer">
              <BPMNDiagram xml={xml}>
                <ClickBehavior
                  onClick={this.toggleNode}
                  selectedNodes={this.state.selectedNodes}
                  nodeTypes={['FlowNode']}
                />
              </BPMNDiagram>
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!this.isValidSelection()} onClick={this.createFilter}>
            {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

export default withErrorHandling(NodeSelection);
