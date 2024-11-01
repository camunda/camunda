/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useCallback, useEffect, useRef} from 'react';
import classnames from 'classnames';
import {InlineNotification} from '@carbon/react';

import {t} from 'translation';
import {showError} from 'notifications';
import {BPMNDiagram, HeatmapOverlay, PageTitle} from 'components';
import {loadProcessDefinitionXml, getFlowNodeNames, incompatibleFilters} from 'services';
import {useErrorHandling} from 'hooks';
import {track} from 'tracking';

import OutlierControlPanel from './OutlierControlPanel';
import OutlierDetailsModal from './OutlierDetailsModal';
import OutlierDetailsTable from './OutlierDetailsTable';
import {loadNodesOutliers, loadDurationData, loadCommonOutliersVariables} from './service';

import './TaskAnalysis.scss';

export default function TaskAnalysis() {
  const [config, setConfig] = useState({
    processDefinitionKey: '',
    processDefinitionVersions: [],
    tenantIds: [],
    minimumDeviationFromAvg: 50,
    disconsiderAutomatedTasks: false,
    filters: [],
  });
  const [xml, setXml] = useState(null);
  const [nodeOutliers, setNodeOutliers] = useState({});
  const [higherNodeOutliersHeatData, setHigherNodeOutliersHeatData] = useState();
  const [flowNodeNames, setFlowNodeNames] = useState({});
  const [selectedOutlierNode, setSelectedOutlierNode] = useState(null);
  const [higherNodeOutlierVariables, setHigherNodeOutlierVariables] = useState({});
  const [isLoadingXml, setIsLoadingXml] = useState(false);
  const [isLoadingFlowNodeNames, setIsLoadingFlowNodeNames] = useState(false);
  const [isLoadingNodeOutliers, setIsLoadingNodeOutliers] = useState(false);
  const {mightFail} = useErrorHandling();
  const nodeOutliersRef = useRef();

  const loadFlowNodeNames = useCallback(
    (config) => {
      setIsLoadingFlowNodeNames(true);
      mightFail(
        getFlowNodeNames(
          config.processDefinitionKey,
          config.processDefinitionVersions[0],
          config.tenantIds[0]
        ),
        (flowNodeNames) => setFlowNodeNames(flowNodeNames),
        showError,
        () => setIsLoadingFlowNodeNames(false)
      );
    },
    [mightFail]
  );

  async function updateConfig(updates) {
    const newConfig = {...config, ...updates};
    const {processDefinitionKey, processDefinitionVersions, tenantIds} = updates;

    if (processDefinitionKey && processDefinitionVersions && tenantIds) {
      setIsLoadingXml(true);
      await mightFail(
        loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersions[0], tenantIds[0]),
        (xml) => {
          setXml(xml);
          track('startOutlierAnalysis', {processDefinitionKey});
        },
        showError,
        () => setIsLoadingXml(false)
      );
    } else if (
      !newConfig.processDefinitionKey ||
      !newConfig.processDefinitionVersions ||
      !newConfig.tenantIds
    ) {
      setXml(null);
    }

    setConfig(() => {
      return newConfig;
    });
  }

  const loadOutlierVariables = useCallback(async (outliersHeatData, nodeOutliers, config) => {
    return Object.keys(outliersHeatData).reduce(async (nodeOutlierVariables, nodeOutlierId) => {
      const outlierVariables = await loadCommonOutliersVariables({
        ...config,
        flowNodeId: nodeOutlierId,
        higherOutlierBound: nodeOutliers[nodeOutlierId].higherOutlier.boundValue,
      });

      nodeOutlierVariables[nodeOutlierId] = outlierVariables;
      return nodeOutlierVariables;
    }, {});
  }, []);

  const loadOutlierData = useCallback(
    (config) => {
      setIsLoadingNodeOutliers(true);
      loadFlowNodeNames(config);
      mightFail(
        loadNodesOutliers(config),
        async (nodeOutliers) => {
          const higherOutliersHeatData = Object.keys(nodeOutliers).reduce(
            // taking only high outliers into consideration
            (nodeOutliersHeats, nodeOutlierId) => {
              if (
                nodeOutliers[nodeOutlierId].higherOutlierHeat &&
                nodeOutliers[nodeOutlierId].higherOutlier
              ) {
                return {
                  ...nodeOutliersHeats,
                  [nodeOutlierId]: nodeOutliers[nodeOutlierId].higherOutlierHeat,
                };
              }
              return nodeOutliersHeats;
            },
            {}
          );

          const higherNodeOutlierVariables = await loadOutlierVariables(
            higherOutliersHeatData,
            nodeOutliers,
            config
          );

          setNodeOutliers(nodeOutliers);
          nodeOutliersRef.current = nodeOutliers;
          setHigherNodeOutliersHeatData(higherOutliersHeatData);
          setHigherNodeOutlierVariables(higherNodeOutlierVariables);
        },
        showError,
        () => setIsLoadingNodeOutliers(false)
      );
    },
    [loadOutlierVariables, loadFlowNodeNames, mightFail]
  );

  function onNodeClick({element: {id}}) {
    // we need to use a ref here because this will create a closure
    const clickedNodeOutlier = nodeOutliersRef.current[id];
    if (!clickedNodeOutlier?.higherOutlier) {
      return;
    }
    loadChartData(id, clickedNodeOutlier);
  }

  function loadChartData(id, nodeData) {
    setIsLoadingXml(true);
    mightFail(
      loadDurationData({
        ...config,
        flowNodeId: id,
        higherOutlierBound: nodeData.higherOutlier.boundValue,
      }),
      (data) => {
        setSelectedOutlierNode({
          name: flowNodeNames[id] || id,
          id,
          data,
          ...nodeData,
        });
      },
      undefined,
      () => setIsLoadingXml(false)
    );
  }

  useEffect(() => {
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    if (procDefConfigured) {
      loadOutlierData(config);
    }
  }, [config, loadOutlierData]);

  const loading = isLoadingXml || isLoadingFlowNodeNames || isLoadingNodeOutliers;
  const empty =
    !!xml &&
    !loading &&
    !!higherNodeOutliersHeatData &&
    Object.keys(higherNodeOutliersHeatData).length === 0;
  const matchingInstancesCount = Object.values(nodeOutliers).reduce(
    (matchingInstancesCount, nodeOutlierData) => {
      if (nodeOutlierData?.totalCount) {
        matchingInstancesCount += nodeOutlierData.totalCount;
      }
      return matchingInstancesCount;
    },
    0
  );
  const hasData = !!Object.keys(nodeOutliers).length;

  return (
    <div className="TaskAnalysis">
      <PageTitle pageName={t('analysis.task.label')} />
      <OutlierControlPanel {...config} onChange={updateConfig} xml={xml} />
      {config.filters && incompatibleFilters(config.filters) && (
        <InlineNotification
          kind="warning"
          hideCloseButton
          subtitle={t('common.filter.incompatibleFilters')}
          className="incompatibleFiltersWarning"
        />
      )}
      {hasData && <p>{t('analysis.task.result', {count: matchingInstancesCount})}</p>}
      <div className={classnames('TaskAnalysis__diagram', {empty})}>
        {hasData && (
          <BPMNDiagram xml={xml} loading={isLoadingXml}>
            <HeatmapOverlay data={higherNodeOutliersHeatData} onNodeClick={onNodeClick} />
          </BPMNDiagram>
        )}
        {empty && <div className="noOutliers">{t('analysis.task.notFound')}</div>}
      </div>
      {selectedOutlierNode && (
        <OutlierDetailsModal
          onClose={() => setSelectedOutlierNode(null)}
          selectedOutlierNode={selectedOutlierNode}
          config={config}
        />
      )}
      {hasData && (
        <>
          <h4 className="subtitle">{t('analysis.task.table.heading')}</h4>
          <OutlierDetailsTable
            flowNodeNames={flowNodeNames}
            loading={isLoadingFlowNodeNames || isLoadingNodeOutliers}
            onDetailsClick={loadChartData}
            outlierVariables={higherNodeOutlierVariables}
            nodeOutliers={nodeOutliers}
            config={config}
          />
        </>
      )}
    </div>
  );
}
