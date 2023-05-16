/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {Button} from '@carbon/react';

import {
  Modal,
  Button as LegacyButton,
  BPMNDiagram,
  ClickBehavior,
  LoadingIndicator,
  RegistryElement,
  ModdleElement,
} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {FilterProps} from '../types';

import './NodeSelection.scss';

interface NodeSelectionProps
  extends WithErrorHandlingProps,
    FilterProps<{
      values?: string[];
      operator?: string;
    }> {
  filterLevel: 'view';
  filterType: 'executedFlowNodes' | 'executingFlowNodes' | 'canceledFlowNodes';
}

export function NodeSelection({
  filterData,
  definitions,
  mightFail,
  close,
  addFilter,
}: NodeSelectionProps) {
  const [allFlowNodes, setAllFlowNodes] = useState<string[]>([]);
  const [selectedNodes, setSelectedNodes] = useState<string[]>([]);
  const [applyTo, setApplyTo] = useState(() => {
    const validDefinitions = definitions.filter(
      (definition) => definition.versions?.length && definition.tenantIds?.length
    );

    return (
      validDefinitions.find(({identifier}) => filterData?.appliedTo?.[0] === identifier) ||
      validDefinitions[0]
    );
  });
  const [xml, setXml] = useState(null);

  useEffect(() => {
    if (applyTo) {
      setSelectedNodes([]);
      setXml(null);
      mightFail(
        loadProcessDefinitionXml(applyTo.key, applyTo.versions?.[0], applyTo.tenantIds?.[0]),
        async (xml) => {
          const viewer = new Viewer();
          await viewer.importXML(xml);

          const flowNodes = new Set<string>();
          viewer
            .get<RegistryElement[]>('elementRegistry')
            .filter((element) => element.businessObject.$instanceOf('bpmn:FlowNode'))
            .map((element) => element.businessObject)
            .forEach((element) => flowNodes.add(element.id));
          const allFlowNodes = Array.from(flowNodes);

          let preExistingValues;
          if (filterData?.data.values) {
            preExistingValues = allFlowNodes.filter((id) => !filterData?.data.values?.includes(id));
          }

          setAllFlowNodes(allFlowNodes);
          setSelectedNodes(preExistingValues || allFlowNodes);
          setXml(xml);
          setApplyTo(applyTo);
        },
        showError
      );
    }
  }, [applyTo, mightFail, filterData]);

  const toggleNode = (toggledNode: ModdleElement) => {
    if (selectedNodes.includes(toggledNode.id)) {
      setSelectedNodes(selectedNodes.filter((node) => node !== toggledNode.id));
    } else {
      setSelectedNodes(selectedNodes.concat([toggledNode.id]));
    }
  };

  const createFilter = () => {
    addFilter({
      type: 'executedFlowNodes',
      data: {operator: 'not in', values: allFlowNodes.filter((id) => !selectedNodes.includes(id))},
      appliedTo: [applyTo?.identifier],
    });
  };

  const isNodeSelected = () => {
    return selectedNodes.length > 0;
  };

  const isAllSelected = () => {
    return allFlowNodes.length === selectedNodes.length;
  };

  const selectAll = () => {
    setSelectedNodes(allFlowNodes);
  };

  const deselectAll = () => {
    setSelectedNodes([]);
  };

  const isValidSelection = () => {
    return isNodeSelected() && !isAllSelected();
  };

  return (
    <Modal open onClose={close} className="NodeSelection" size="lg">
      <Modal.Header>{t('common.filter.types.flowNodeSelection')}</Modal.Header>
      <Modal.Content className="modalContent">
        <FilterSingleDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        {!xml && <LoadingIndicator />}
        {xml && (
          <>
            <div className="diagramActions">
              <LegacyButton disabled={false} onClick={selectAll}>
                {t('common.selectAll')}
              </LegacyButton>
              <LegacyButton disabled={!isNodeSelected()} onClick={deselectAll}>
                {t('common.deselectAll')}
              </LegacyButton>
            </div>
            <div className="diagramContainer">
              <BPMNDiagram xml={xml}>
                <ClickBehavior
                  onClick={toggleNode}
                  selectedNodes={selectedNodes}
                  nodeTypes={['FlowNode']}
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
        <Button disabled={!isValidSelection()} className="confirm" onClick={createFilter}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(NodeSelection);
