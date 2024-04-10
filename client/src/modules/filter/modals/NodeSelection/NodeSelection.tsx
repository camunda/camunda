/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {Button, ButtonSet} from '@carbon/react';
import classnames from 'classnames';

import {
  Modal,
  BPMNDiagram,
  Loading,
  ClickBehavior,
  RegistryElement,
  ModdleElement,
} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {FilterProps} from '../types';

import './NodeSelection.scss';

export default function NodeSelection({
  filterData,
  definitions,
  close,
  addFilter,
  modalTitle = t('common.filter.types.flowNodeSelection'),
  className,
}: FilterProps<{
  values?: string[];
  operator?: string;
}>) {
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
  const [xml, setXml] = useState<string | null>(null);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    if (applyTo) {
      setSelectedNodes([]);
      setXml(null);
      mightFail(
        loadProcessDefinitionXml(applyTo.key, applyTo.versions?.[0], applyTo.tenantIds?.[0]),
        async (xml) => {
          if (!xml) {
            return;
          }

          const viewer = new Viewer();
          await viewer.importXML(xml);

          const flowNodes = new Set<string>();
          viewer
            .get<RegistryElement[]>('elementRegistry')
            .filter((element) => element.businessObject.$instanceOf('bpmn:FlowNode'))
            .map((element) => element.businessObject)
            .forEach((element) => flowNodes.add(element.id));
          const allFlowNodes = Array.from(flowNodes);

          let preExistingValues = filterData?.data.values;
          if (preExistingValues && filterData?.data.operator === 'not in') {
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
    const selectionPercentage = (selectedNodes.length / allFlowNodes.length) * 100;
    const deselectedFlowNodes = allFlowNodes.filter((id) => !selectedNodes.includes(id));

    addFilter({
      type: 'executedFlowNodes',
      data:
        // Determine whether to use "not in" or "in" for better backend performance
        selectionPercentage < 50
          ? {operator: 'in', values: selectedNodes}
          : {operator: 'not in', values: deselectedFlowNodes},
      appliedTo: applyTo ? [applyTo.identifier] : [],
    });
  };

  const isAllSelected = () => {
    return allFlowNodes.length === selectedNodes.length;
  };

  const isValidSelection = () => {
    return selectedNodes.length > 0 && !isAllSelected();
  };

  return (
    <Modal open onClose={close} className={classnames('NodeSelection', className)} size="lg">
      <Modal.Header>{modalTitle}</Modal.Header>
      <Modal.Content className="modalContent">
        <FilterSingleDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        {!xml ? (
          <Loading />
        ) : (
          <>
            <p>{t('common.filter.UnselectFlowNodes')}</p>
            <div className="diagramActions">
              <ButtonSet>
                <Button size="md" kind="tertiary" onClick={() => setSelectedNodes(allFlowNodes)}>
                  {t('common.selectAll')}
                </Button>
                <Button size="md" kind="tertiary" onClick={() => setSelectedNodes([])}>
                  {t('common.deselectAll')}
                </Button>
              </ButtonSet>
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
