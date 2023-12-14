/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useCallback, useEffect} from 'react';
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
  const [data, setData] = useState({});
  const [heatData, setHeatData] = useState({});
  const [flowNodeNames, setFlowNodeNames] = useState({});
  const [selectedNode, setSelectedNode] = useState(null);
  const [outlierVariables, setOutlierVariables] = useState({});
  const [loading, setLoading] = useState(false);
  const {mightFail} = useErrorHandling();

  const loadFlowNodeNames = useCallback(
    (config) => {
      mightFail(
        getFlowNodeNames(
          config.processDefinitionKey,
          config.processDefinitionVersions[0],
          config.tenantIds[0]
        ),
        (flowNodeNames) => setFlowNodeNames(flowNodeNames),
        showError
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

  const loadOutlierVariables = useCallback(async (heatData, data, config) => {
    return Object.keys(heatData).reduce(async (acc, id) => {
      const outlierVariables = await loadCommonOutliersVariables({
        ...config,
        flowNodeId: id,
        higherOutlierBound: data[id].higherOutlier.boundValue,
      });

      acc[id] = outlierVariables;
      return acc;
    }, {});
  }, []);

  const loadOutlierData = useCallback(
    (config) => {
      setLoading(true);
      setHeatData({});
      loadFlowNodeNames(config);
      mightFail(
        loadNodesOutliers(config),
        async (data) => {
          const heatData = Object.keys(data).reduce((acc, key) => {
            // taking only high outliers into consideration
            if (data[key].higherOutlierHeat && data[key].higherOutlier) {
              return {...acc, [key]: data[key].higherOutlierHeat};
            }
            return acc;
          }, {});

          const outlierVariables = await loadOutlierVariables(heatData, data, config);

          setData(data);
          setHeatData(heatData);
          setOutlierVariables(outlierVariables);
        },
        showError,
        () => setLoading(false)
      );
    },
    [loadOutlierVariables, loadFlowNodeNames, mightFail]
  );

  function onNodeClick({element: {id}}) {
    const nodeData = data[id];
    if (!nodeData?.higherOutlier) {
      return;
    }
    loadChartData(id, nodeData);
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
        setSelectedNode({
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

  const empty = xml && !loading && Object.keys(heatData).length === 0;
  const matchingInstancesCount = Object.values(data).reduce((result, data) => {
    if (data?.higherOutlier?.count) {
      result += data.higherOutlier.count;
    }
    return result;
  }, 0);
  const hasData = !!Object.keys(data).length;

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
            <HeatmapOverlay data={heatData} onNodeClick={onNodeClick} />
          </BPMNDiagram>
        )}
        {empty && <div className="noOutliers">{t('analysis.task.notFound')}</div>}
      </div>
      {selectedNode && (
        <OutlierDetailsModal
          onClose={() => setSelectedNode(null)}
          selectedNode={selectedNode}
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
            outlierVariables={outlierVariables}
            tasksData={data}
          />
        </>
      )}
    </div>
  );
}
