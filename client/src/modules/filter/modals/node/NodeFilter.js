/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Modal, ButtonGroup, Button, BPMNDiagram, ClickBehavior} from 'components';
import {t} from 'translation';
import {loadProcessDefinitionXml} from 'services';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import NodeListPreview from './NodeListPreview';

import './NodeFilter.scss';

export class NodeFilter extends React.Component {
  constructor(props) {
    super(props);

    const {filterData} = this.props;

    this.state = {
      selectedNodes: filterData?.data.values ?? [],
      operator: filterData?.data ? filterData?.data.operator : 'in',
      type: filterData?.type ?? 'executedFlowNodes',
      applyTo: null,
      xml: null,
    };
  }

  componentDidMount() {
    const validDefinitions = this.props.definitions.filter(
      (definition) => definition.versions.length && definition.tenantIds.length
    );

    this.updateXml(
      validDefinitions.find(({identifier}) => this.props.filterData?.appliedTo[0] === identifier) ||
        validDefinitions[0]
    );
  }

  async componentDidUpdate(prevProps, prevState) {
    if (prevState.applyTo && prevState.applyTo !== this.state.applyTo) {
      this.setState({selectedNodes: []});
      await this.updateXml(this.state.applyTo);
    }
  }

  updateXml = (applyTo) => {
    this.setState({xml: null});
    return this.props.mightFail(
      loadProcessDefinitionXml(applyTo.key, applyTo.versions[0], applyTo.tenantIds[0]),
      (xml) => {
        this.setState({xml, applyTo});
      },
      showError
    );
  };

  toggleNode = (toggledNode) => {
    if (this.state.selectedNodes.includes(toggledNode)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter((node) => node !== toggledNode),
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode]),
      });
    }
  };

  createFilter = () => {
    const values = this.state.selectedNodes.map((node) => node.id);
    const {operator, type} = this.state;
    this.props.addFilter({
      type,
      data: {operator, values},
      appliedTo: [this.state.applyTo.identifier],
    });
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  setSelectedNodes = (nodes) => {
    this.setState({
      selectedNodes: nodes,
    });
  };

  render() {
    const {close, filterData, definitions} = this.props;
    const {selectedNodes, operator, type, applyTo, xml} = this.state;

    return (
      <Modal
        open
        onClose={close}
        onConfirm={this.isNodeSelected() ? this.createFilter : undefined}
        className="NodeFilter"
        size="max"
      >
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.flowNode`),
          })}
        </Modal.Header>
        <Modal.Content className="modalContent">
          <FilterSingleDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={(applyTo) => this.setState({applyTo})}
          />
          <div className="preview">
            <NodeListPreview nodes={selectedNodes} operator={operator} type={type} />
          </div>
          <ButtonGroup>
            <Button
              active={type === 'executingFlowNodes'}
              onClick={() => this.setState({operator: undefined, type: 'executingFlowNodes'})}
            >
              {t('common.filter.nodeModal.executingFlowNodes')}
            </Button>
            <Button
              active={operator === 'in'}
              onClick={() => this.setState({operator: 'in', type: 'executedFlowNodes'})}
            >
              {t('common.filter.nodeModal.executedFlowNodes')}
            </Button>
            <Button
              active={operator === 'not in'}
              onClick={() => this.setState({operator: 'not in', type: 'executedFlowNodes'})}
            >
              {t('common.filter.nodeModal.notExecutedFlowNodes')}
            </Button>
            <Button
              active={type === 'canceledFlowNodes'}
              onClick={() => this.setState({operator: undefined, type: 'canceledFlowNodes'})}
            >
              {t('common.filter.nodeModal.canceledFlowNodes')}
            </Button>
          </ButtonGroup>
          {xml && (
            <div className="diagramContainer">
              <BPMNDiagram xml={xml}>
                <ClickBehavior
                  setSelectedNodes={this.setSelectedNodes}
                  onClick={this.toggleNode}
                  selectedNodes={this.state.selectedNodes}
                />
              </BPMNDiagram>
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!this.isNodeSelected()} onClick={this.createFilter}>
            {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

export default withErrorHandling(NodeFilter);
