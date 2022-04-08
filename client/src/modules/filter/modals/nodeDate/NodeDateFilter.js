/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import classnames from 'classnames';

import {Modal, Button, Form, DateRangeInput, BPMNDiagram, ClickBehavior} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {convertFilterToState, convertStateToFilter, isValid} from '../date/service';

import './NodeDateFilter.scss';

export function NodeDateFilter({
  filterData,
  close,
  definitions,
  className,
  mightFail,
  filterType,
  addFilter,
  filterLevel,
}) {
  const [selectedNodes, setSelectedNodes] = useState([]);
  const [applyTo, setApplyTo] = useState(null);
  const [xml, setXml] = useState(null);
  const [dateRange, setDateRange] = useState({
    type: '',
    unit: '',
    customNum: '2',
    startDate: null,
    endDate: null,
  });

  useEffect(() => {
    const validDefinitions = definitions.filter(
      (definition) => definition.versions.length && definition.tenantIds.length
    );

    setApplyTo(
      validDefinitions.find(({identifier}) => filterData?.appliedTo[0] === identifier) ||
        validDefinitions[0]
    );
  }, [definitions, filterData?.appliedTo]);

  useEffect(() => {
    if (applyTo) {
      setSelectedNodes([]);
      setXml(null);
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

    const {flowNodeIds, ...dateRangeData} = filterData.data;

    setDateRange(convertFilterToState(dateRangeData));
    setSelectedNodes(flowNodeIds);
  }, [filterData]);

  const toggleNode = (toggledNode) => {
    if (selectedNodes.includes(toggledNode.id)) {
      setSelectedNodes(selectedNodes.filter((node) => node !== toggledNode.id));
    } else {
      setSelectedNodes(selectedNodes.concat([toggledNode.id]));
    }
  };

  const confirm = () => {
    addFilter({
      type: filterType,
      data: {
        flowNodeIds: filterLevel === 'instance' ? selectedNodes : null,
        ...convertStateToFilter(dateRange),
      },
      appliedTo: [applyTo?.identifier],
    });
  };

  const {type, unit, customNum, startDate, endDate} = dateRange;

  return (
    <Modal
      open
      onClose={close}
      className={classnames('NodeDateFilter', className)}
      size={filterLevel === 'instance' ? 'max' : undefined}
    >
      <Modal.Header>
        {t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`),
        })}
      </Modal.Header>
      <Modal.Content>
        <FilterSingleDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        <Form>
          <p className="info">
            {t('common.filter.nodeDateModal.info.' + filterType + '.' + filterLevel)}
          </p>
          <DateRangeInput
            type={type}
            unit={unit}
            startDate={startDate}
            endDate={endDate}
            customNum={customNum}
            onChange={(change) => setDateRange({...dateRange, ...change})}
          />
        </Form>
        {xml && filterLevel === 'instance' && (
          <div className="diagramContainer">
            <BPMNDiagram xml={xml}>
              <ClickBehavior
                onClick={toggleNode}
                selectedNodes={selectedNodes}
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
        <Button
          main
          primary
          onClick={confirm}
          disabled={
            (filterLevel === 'instance' && selectedNodes?.length === 0) || !isValid(dateRange)
          }
        >
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(NodeDateFilter);
