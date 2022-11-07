/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Modal, ButtonGroup, Button, BPMNDiagram, ClickBehavior, LoadingIndicator} from 'components';
import {t} from 'translation';
import {loadProcessDefinitionXml} from 'services';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import NodeListPreview from './NodeListPreview';

import './NodeFilter.scss';

export function NodeFilter({filterData, definitions, mightFail, close, addFilter}) {
  const [selectedNodes, setSelectedNodes] = useState([]);
  const [applyTo, setApplyTo] = useState(() => {
    const validDefinitions = definitions.filter(
      (definition) => definition.versions.length && definition.tenantIds.length
    );

    return (
      validDefinitions.find(({identifier}) => filterData?.appliedTo[0] === identifier) ||
      validDefinitions[0]
    );
  });
  const [xml, setXml] = useState(null);
  const [operator, setOperator] = useState(filterData?.data ? filterData?.data.operator : 'in');
  const [type, setType] = useState(filterData?.type ?? 'executedFlowNodes');

  useEffect(() => {
    if (applyTo) {
      setXml(null);
      setSelectedNodes([]);
      mightFail(
        loadProcessDefinitionXml(applyTo.key, applyTo.versions[0], applyTo.tenantIds[0]),
        setXml,
        showError
      );
    }
  }, [applyTo, mightFail]);

  useEffect(() => {
    if (!filterData) {
      return;
    }

    setSelectedNodes(filterData.data.values);
  }, [filterData]);

  const toggleNode = (toggledNode) => {
    if (selectedNodes.includes(toggledNode)) {
      setSelectedNodes(selectedNodes.filter((node) => node !== toggledNode));
    } else {
      setSelectedNodes(selectedNodes.concat([toggledNode]));
    }
  };

  const createFilter = () => {
    const values = selectedNodes.map((node) => node.id);
    addFilter({
      type,
      data: {operator, values},
      appliedTo: [applyTo.identifier],
    });
  };

  const isNodeSelected = () => {
    return selectedNodes.length > 0;
  };

  const setTypeAndOperator = ({type, operator}) => {
    setType(type);
    setOperator(operator);
  };

  return (
    <Modal
      open
      onClose={close}
      onConfirm={isNodeSelected() ? createFilter : undefined}
      className="NodeFilter"
      size="max"
    >
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t('common.filter.types.flowNode'),
        })}
      </Modal.Header>
      <Modal.Content className="modalContent">
        <FilterSingleDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        {!xml && <LoadingIndicator />}
        {xml && (
          <>
            <div className="preview">
              <NodeListPreview nodes={selectedNodes} operator={operator} type={type} />
            </div>
            <ButtonGroup>
              <Button
                active={type === 'executingFlowNodes'}
                onClick={() =>
                  setTypeAndOperator({operator: undefined, type: 'executingFlowNodes'})
                }
              >
                {t('common.filter.nodeModal.executingFlowNodes')}
              </Button>
              <Button
                active={operator === 'in'}
                onClick={() => setTypeAndOperator({operator: 'in', type: 'executedFlowNodes'})}
              >
                {t('common.filter.nodeModal.executedFlowNodes')}
              </Button>
              <Button
                active={operator === 'not in'}
                onClick={() => setTypeAndOperator({operator: 'not in', type: 'executedFlowNodes'})}
              >
                {t('common.filter.nodeModal.notExecutedFlowNodes')}
              </Button>
              <Button
                active={type === 'canceledFlowNodes'}
                onClick={() => setTypeAndOperator({operator: undefined, type: 'canceledFlowNodes'})}
              >
                {t('common.filter.nodeModal.canceledFlowNodes')}
              </Button>
            </ButtonGroup>
            <div className="diagramContainer">
              <BPMNDiagram xml={xml}>
                <ClickBehavior
                  setSelectedNodes={setSelectedNodes}
                  onClick={toggleNode}
                  selectedNodes={selectedNodes}
                />
              </BPMNDiagram>
            </div>
          </>
        )}
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!isNodeSelected()} onClick={createFilter}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(NodeFilter);
