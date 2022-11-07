/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import Viewer from 'bpmn-js/lib/NavigatedViewer';

import {Modal, Button, BPMNDiagram, ClickBehavior, LoadingIndicator} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import './NodeSelection.scss';

export function NodeSelection({filterData, definitions, mightFail, close, addFilter}) {
  const [allFlowNodes, setAllFlowNodes] = useState([]);
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

  useEffect(() => {
    if (applyTo) {
      setSelectedNodes([]);
      setXml(null);
      mightFail(
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

          setAllFlowNodes(allFlowNodes);
          setSelectedNodes(preExistingValues || allFlowNodes);
          setXml(xml);
          setApplyTo(applyTo);
        },
        showError
      );
    }
  }, [applyTo, mightFail, filterData]);

  const toggleNode = (toggledNode) => {
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
      appliedTo: [applyTo.identifier],
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
    <Modal
      open
      onClose={close}
      onConfirm={isValidSelection() ? createFilter : undefined}
      className="NodeSelection"
      size="max"
    >
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
              <Button disabled={false} onClick={selectAll}>
                {t('common.selectAll')}
              </Button>
              <Button disabled={!isNodeSelected()} onClick={deselectAll}>
                {t('common.deselectAll')}
              </Button>
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
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!isValidSelection()} onClick={createFilter}>
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(NodeSelection);
