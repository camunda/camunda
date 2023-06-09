/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Button} from '@carbon/react';

import {
  Modal,
  ButtonGroup,
  Button as LegacyButton,
  BPMNDiagram,
  ClickBehavior,
  LoadingIndicator,
  ModdleElement,
} from 'components';
import {t} from 'translation';
import {loadProcessDefinitionXml} from 'services';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {FilterProps} from '../types';

import NodeListPreview from './NodeListPreview';

import './NodeFilter.scss';

interface NodeFilterProps
  extends WithErrorHandlingProps,
    FilterProps<{
      values?: string[];
      operator?: string;
    }> {
  filterLevel: 'instance';
  filterType: 'executedFlowNodes' | 'executingFlowNodes' | 'canceledFlowNodes';
}

export function NodeFilter({
  filterData,
  definitions,
  mightFail,
  close,
  addFilter,
}: NodeFilterProps) {
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
      appliedTo: [applyTo?.identifier],
    });
  };

  const isNodeSelected = () => {
    return selectedNodes.length > 0;
  };

  const setTypeAndOperator = ({type, operator}: {type: string; operator?: string}) => {
    setType(type);
    setOperator(operator);
  };

  return (
    <Modal open onClose={close} className="NodeFilter" size="lg">
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
              <NodeListPreview
                nodes={selectedNodes as ModdleElement[]}
                operator={operator}
                type={type}
              />
            </div>
            <ButtonGroup>
              <LegacyButton
                active={type === 'executingFlowNodes'}
                onClick={() =>
                  setTypeAndOperator({operator: undefined, type: 'executingFlowNodes'})
                }
              >
                {t('common.filter.nodeModal.executingFlowNodes')}
              </LegacyButton>
              <LegacyButton
                active={operator === 'in'}
                onClick={() => setTypeAndOperator({operator: 'in', type: 'executedFlowNodes'})}
              >
                {t('common.filter.nodeModal.executedFlowNodes')}
              </LegacyButton>
              <LegacyButton
                active={operator === 'not in'}
                onClick={() => setTypeAndOperator({operator: 'not in', type: 'executedFlowNodes'})}
              >
                {t('common.filter.nodeModal.notExecutedFlowNodes')}
              </LegacyButton>
              <LegacyButton
                active={type === 'canceledFlowNodes'}
                onClick={() => setTypeAndOperator({operator: undefined, type: 'canceledFlowNodes'})}
              >
                {t('common.filter.nodeModal.canceledFlowNodes')}
              </LegacyButton>
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
