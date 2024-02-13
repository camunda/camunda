/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useCallback, useEffect, useRef} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {showError} from 'notifications';
import {BPMNDiagram, HeatmapOverlay, MessageBox, PageTitle} from 'components';
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
  const [higherNodeOutliersHeatData, setHigherNodeOutliersHeatData] = useState({});
  const [flowNodeNames, setFlowNodeNames] = useState({});
  const [selectedOutlierNode, setSelectedOutlierNode] = useState(null);
  const [higherNodeOutlierVariables, setHigherNodeOutlierVariables] = useState({});
  const [loading, setLoading] = useState(false);
  const {mightFail} = useErrorHandling();
  const nodeOutliersRef = useRef();

  const loadFlowNodeNames = useCallback(
    (config) => {
      setLoading(true);
      mightFail(
        getFlowNodeNames(
          config.processDefinitionKey,
          config.processDefinitionVersions[0],
          config.tenantIds[0]
        ),
        (flowNodeNames) => setFlowNodeNames(flowNodeNames),
        showError,
        () => setLoading(false)
      );
    },
    [mightFail]
  );

  async function updateConfig(updates) {
    const newConfig = {...config, ...updates};
    const {processDefinitionKey, processDefinitionVersions, tenantIds} = updates;

    if (processDefinitionKey && processDefinitionVersions && tenantIds) {
      setLoading(true);
      await mightFail(
        loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersions[0], tenantIds[0]),
        (xml) => {
          setXml(xml);
          track('startOutlierAnalysis', {processDefinitionKey});
        },
        showError,
        () => setLoading(false)
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
      setLoading(true);
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
        () => setLoading(false)
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
    setLoading(true);
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
      () => setLoading(false)
    );
  }

  useEffect(() => {
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    if (procDefConfigured) {
      loadOutlierData(config);
    }
  }, [config, loadOutlierData]);

  const empty = xml && !loading && Object.keys(higherNodeOutliersHeatData).length === 0;
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
        <MessageBox type="warning">{t('common.filter.incompatibleFilters')}</MessageBox>
      )}
      {hasData && <h2>{t('analysis.task.result', {count: matchingInstancesCount})}</h2>}
      <div className={classnames('TaskAnalysis__diagram', {empty})}>
        {xml && hasData && (
          <BPMNDiagram xml={xml} loading={loading}>
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
          <h2>{t('analysis.task.table.heading')}</h2>
          <OutlierDetailsTable
            flowNodeNames={flowNodeNames}
            loading={loading}
            onDetailsClick={loadChartData}
            outlierVariables={higherNodeOutlierVariables}
            nodeOutliers={nodeOutliers}
          />
        </>
      )}
    </div>
  );
}
