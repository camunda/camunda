/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Button, RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {Modal, BPMNDiagram, Loading, ClickBehavior, ModdleElement} from 'components';
import {t} from 'translation';
import {loadProcessDefinitionXml} from 'services';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {FilterProps} from '../types';

import NodeListPreview from './NodeListPreview';

import './NodeFilter.scss';

export function NodeFilter({
  filterData,
  definitions,
  mightFail,
  close,
  addFilter,
}: FilterProps<'executedFlowNodes' | 'executingFlowNodes' | 'canceledFlowNodes'> &
  WithErrorHandlingProps) {
  const [selectedNodes, setSelectedNodes] = useState<(string | ModdleElement)[]>([]);
  const [applyTo, setApplyTo] = useState(() => {
    const validDefinitions = definitions.filter(
      (definition) => definition.versions?.length && definition.tenantIds?.length
    );

    return (
      validDefinitions.find(({identifier}) => filterData?.appliedTo[0] === identifier) ||
      validDefinitions[0]
    );
  });
  const [xml, setXml] = useState<string | null>(null);
  const [operator, setOperator] = useState<string | undefined>(
    filterData?.data ? filterData?.data.operator : 'in'
  );
  const [type, setType] = useState(filterData?.type ?? 'executedFlowNodes');

  useEffect(() => {
    if (applyTo) {
      setXml(null);
      setSelectedNodes([]);
      mightFail(
        loadProcessDefinitionXml(applyTo.key, applyTo.versions?.[0], applyTo.tenantIds?.[0]),
        setXml,
        showError
      );
    }
  }, [applyTo, mightFail]);

  useEffect(() => {
    if (!filterData) {
      return;
    }
    setSelectedNodes(filterData.data.values || []);
  }, [filterData]);

  const toggleNode = (toggledNode: ModdleElement) => {
    if (selectedNodes.includes(toggledNode)) {
      setSelectedNodes(selectedNodes.filter((node) => node !== toggledNode));
    } else {
      setSelectedNodes(selectedNodes.concat([toggledNode]));
    }
  };

  const createFilter = () => {
    const values = (selectedNodes as ModdleElement[]).map((node) => node.id);
    addFilter({
      type,
      data: {operator, values},
      appliedTo: applyTo ? [applyTo.identifier] : [],
    });
  };

  const isNodeSelected = () => {
    return selectedNodes.length > 0;
  };

  const setTypeAndOperator = ({
    type,
    operator,
  }: {
    type: 'executedFlowNodes' | 'executingFlowNodes' | 'canceledFlowNodes';
    operator?: string;
  }) => {
    setType(type);
    setOperator(operator);
  };

  return (
    <Modal open onClose={close} className="NodeFilter" size="lg">
      <Modal.Header
        title={t('common.filter.modalHeader', {
          type: t('common.filter.types.flowNode').toString(),
        })}
      />
      <Modal.Content className="modalContent">
        <FilterSingleDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        {xml ? (
          <>
            <Stack gap={6}>
              <div className="preview">
                <NodeListPreview
                  nodes={selectedNodes as ModdleElement[]}
                  operator={operator}
                  type={type}
                />
              </div>
              <RadioButtonGroup
                legendText={t('common.filter.types.flowNodeStatus')}
                name="flowNodeStateRadioGroup"
              >
                <RadioButton
                  checked={type === 'executingFlowNodes'}
                  onClick={() =>
                    setTypeAndOperator({operator: undefined, type: 'executingFlowNodes'})
                  }
                  labelText={t('common.filter.nodeModal.executingFlowNodes')}
                  value="executingFlowNodes"
                />
                <RadioButton
                  checked={operator === 'in'}
                  onClick={() => setTypeAndOperator({operator: 'in', type: 'executedFlowNodes'})}
                  labelText={t('common.filter.nodeModal.executedFlowNodes')}
                  value="in"
                />
                <RadioButton
                  checked={operator === 'not in'}
                  onClick={() =>
                    setTypeAndOperator({operator: 'not in', type: 'executedFlowNodes'})
                  }
                  labelText={t('common.filter.nodeModal.notExecutedFlowNodes')}
                  value="not in"
                />
                <RadioButton
                  checked={type === 'canceledFlowNodes'}
                  onClick={() =>
                    setTypeAndOperator({operator: undefined, type: 'canceledFlowNodes'})
                  }
                  labelText={t('common.filter.nodeModal.canceledFlowNodes')}
                  value="canceledFlowNodes"
                />
              </RadioButtonGroup>
              <p>{t('common.filter.nodeModal.selectFlowNode')}</p>
            </Stack>
            <div className="diagramContainer">
              <BPMNDiagram xml={xml} loading={!xml}>
                <ClickBehavior
                  setSelectedNodes={setSelectedNodes}
                  onClick={toggleNode}
                  selectedNodes={selectedNodes}
                />
              </BPMNDiagram>
            </div>
          </>
        ) : (
          <Loading />
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" disabled={!isNodeSelected()} onClick={createFilter}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(NodeFilter);
