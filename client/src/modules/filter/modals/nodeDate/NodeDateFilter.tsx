/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Button} from '@carbon/react';
import classnames from 'classnames';

import {
  CarbonModal as Modal,
  Form,
  DateRangeInput,
  BPMNDiagram,
  ClickBehavior,
  LoadingIndicator,
  ModdleElement,
} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {Filter, FilterState} from 'types';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {convertFilterToState, convertStateToFilter, isValid} from '../date/service';
import {FilterProps} from '../types';

import './NodeDateFilter.scss';

interface NodeDateFilterProps
  extends WithErrorHandlingProps,
    FilterProps<Partial<Filter & {flowNodeIds: string[] | null}>> {
  filterType: 'flowNodeStartDate' | 'flowNodeEndDate';
  filterLevel: 'instance';
}

export function NodeDateFilter({
  filterData,
  close,
  definitions,
  className,
  mightFail,
  filterType,
  addFilter,
  filterLevel,
}: NodeDateFilterProps) {
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
  const [xml, setXml] = useState(null);
  const [dateRange, setDateRange] = useState<FilterState>({
    type: '',
    unit: '',
    customNum: '2',
    startDate: null,
    endDate: null,
  });

  useEffect(() => {
    if (applyTo) {
      setSelectedNodes([]);
      setXml(null);
      mightFail(
        loadProcessDefinitionXml(applyTo.key, applyTo.versions?.[0], applyTo.tenantIds?.[0]),
        (xml) => {
          setXml(xml);
        },
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
        flowNodeIds: filterLevel === 'instance' ? (selectedNodes as string[]) : null,
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
      size={filterLevel === 'instance' ? 'lg' : 'sm'}
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
        {!xml && <LoadingIndicator />}
        {xml && (
          <>
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
                onChange={(change) => setDateRange({...dateRange, ...change} as FilterState)}
              />
            </Form>
            {filterLevel === 'instance' && (
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
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button
          className="confirm"
          onClick={confirm}
          disabled={
            (filterLevel === 'instance' && selectedNodes?.length === 0) || !isValid(dateRange)
          }
        >
          {filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(NodeDateFilter);
