/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Button, Form, Stack} from '@carbon/react';
import classnames from 'classnames';

import {
  Modal,
  DateRangeInput,
  Loading,
  BPMNDiagram,
  ClickBehavior,
  ModdleElement,
} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {FilterState} from 'types';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {convertFilterToState, convertStateToFilter, isValid} from '../date/service';
import {FilterProps} from '../types';

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
}: FilterProps<'flowNodeStartDate' | 'flowNodeEndDate'> & WithErrorHandlingProps) {
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
  const [dateRange, setDateRange] = useState<FilterState>({
    type: '',
    unit: '',
    customNum: '2',
    startDate: null,
    endDate: null,
  });
  const isInstanceFilter = filterLevel === 'instance';

  useEffect(() => {
    if (applyTo) {
      setSelectedNodes([]);
      setXml(null);
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

    const {flowNodeIds, ...dateRangeData} = filterData.data;

    setDateRange(convertFilterToState(dateRangeData));
    setSelectedNodes(flowNodeIds || []);
  }, [filterData]);

  const toggleNode = (toggledNode: ModdleElement) => {
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
        flowNodeIds: isInstanceFilter ? (selectedNodes as string[]) : null,
        ...convertStateToFilter(dateRange),
      },
      appliedTo: applyTo ? [applyTo.identifier] : [],
    });
  };

  const {type, unit, customNum, startDate, endDate} = dateRange;

  return (
    <Modal
      open
      onClose={close}
      className={classnames('NodeDateFilter', className)}
      size={isInstanceFilter ? 'lg' : 'sm'}
      isOverflowVisible={!isInstanceFilter}
    >
      <Modal.Header
        title={t('common.filter.modalHeader', {
          type: t(`common.filter.types.${filterType}`).toString(),
        })}
      />
      <Modal.Content>
        <FilterSingleDefinitionSelection
          availableDefinitions={definitions}
          applyTo={applyTo}
          setApplyTo={setApplyTo}
        />
        {xml ? (
          <>
            <Form>
              <Stack gap={4}>
                <p className="info">
                  {t('common.filter.nodeDateModal.info.' + filterType + '.' + filterLevel)}
                </p>
                <DateRangeInput
                  type={type}
                  unit={unit}
                  startDate={startDate}
                  endDate={endDate}
                  customNum={customNum}
                  onChange={(change) => setDateRange({...dateRange, ...change} as FilterState)}
                />
              </Stack>
            </Form>
            {isInstanceFilter && (
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
          </>
        ) : (
          <Loading />
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button
          className="confirm"
          onClick={confirm}
          disabled={(isInstanceFilter && selectedNodes?.length === 0) || !isValid(dateRange)}
        >
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(NodeDateFilter);
