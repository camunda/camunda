/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import update from 'immutability-helper';
import deepEqual from 'fast-deep-equal';
import {useHistory, useLocation} from 'react-router-dom';
import {Button, Toggle} from '@carbon/react';
import classnames from 'classnames';

import {nowDirty, nowPristine} from 'saveGuard';
import {
  ReportRenderer,
  EntityNameForm,
  InstanceCount,
  InstanceViewTable,
  Loading,
} from 'components';
import {updateEntity, createEntity, evaluateReport, getCollection} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';
import {useErrorHandling, useChangedState} from 'hooks';
import {track} from 'tracking';

import ReportControlPanel from './controlPanels/ReportControlPanel';
import ConflictModal from './ConflictModal';
import {Configuration} from './controlPanels/Configuration';
import ReportWarnings from './ReportWarnings';
import Visualization from './Visualization';
import {CollapsibleContainer} from './CollapsibleContainer';

import './ReportEdit.scss';

export function ReportEdit({report: initialReport, isNew, error, updateOverview}) {
  const [loadingReportData, setLoadingReportData] = useState(false);
  const [redirect, setRedirect] = useState('');
  const [conflict, setConflict] = useState(null);
  const [originalData] = useState(initialReport);
  const [updatePromise, setUpdatePromise] = useState(null);
  const [report, setReport] = useChangedState(initialReport);
  const [serverError, setServerError] = useState(error);
  const [shouldAutoReloadPreview, setShouldAutoReloadPreview] = useState(
    sessionStorage.getItem('shouldAutoReloadPreview') === 'true'
  );
  const [frozenReport, setFrozenReport] = useChangedState(initialReport);
  const [runButtonLoading, setRunButtonLoading] = useState(false);
  const [showReportRenderer, setShowReportRenderer] = useState(true);
  const containerRef = useRef(null);
  const {mightFail} = useErrorHandling();
  const location = useLocation();
  const isMounted = useRef(false);
  const history = useHistory();

  function showSaveError(error) {
    setConflict(null);
    showError(error);
  }

  const saveUpdatedReport = useCallback(
    function saveUpdatedReport({endpoint, id, name, description, data}) {
      return new Promise((resolve, reject) => {
        mightFail(
          updateEntity(
            endpoint,
            id,
            {name, description, data},
            {query: {force: conflict !== null}}
          ),
          () => resolve(id),
          (error) => {
            if (error.status === 409 && error.conflictedItems) {
              setReport(update(report, {name: {$set: name}, description: {$set: description}}));
              setConflict(error.conflictedItems);
              setUpdatePromise(resolve);
            } else {
              showSaveError(error);
              reject();
            }
          }
        );
      });
    },
    [conflict, mightFail, report, setReport]
  );

  const save = useCallback(
    function save() {
      return new Promise(async (resolve, reject) => {
        const {id, name, description, data} = report;
        const endpoint = `report/process/single`;

        if (isNew) {
          const collectionId = getCollection(location.pathname);

          mightFail(
            createEntity(endpoint, {collectionId, name, description, data}),
            resolve,
            (error) => {
              showSaveError(error);
              reject();
            }
          );
        } else {
          resolve(await saveUpdatedReport({endpoint, id, name, description, data}));
        }
      });
    },
    [isNew, location.pathname, mightFail, report, saveUpdatedReport]
  );

  async function saveAndGoBack() {
    const id = await save();
    if (updatePromise) {
      updatePromise(id);
      setUpdatePromise(null);
    }

    if (isReportDirty() || !shouldAutoReloadPreview) {
      await reEvaluateReport(report.data);
    }

    if (id) {
      nowPristine();
      updateOverview(update(report, {id: {$set: id}}), serverError);

      const params = new URLSearchParams(location.search);
      const returnTo = params.get('returnTo');
      let redirect = './';

      if (returnTo) {
        redirect = returnTo;
      } else if (isNew) {
        redirect = `../${id}/`;
      }

      setRedirect(redirect);
    }
  }

  function cancel(evt) {
    nowPristine();

    const params = new URLSearchParams(location.search);
    const returnTo = params.get('returnTo');

    if (returnTo) {
      evt.preventDefault();
      setRedirect(returnTo);
    }

    setReport(originalData);
  }

  async function updateReport(change, needsReevaluation) {
    const newReport = update(report, {data: change});
    const newReportData = update(report.data, change);

    setReport(newReport);
    dirtyCheck(newReport);

    if (isRearrangementChanged(change)) {
      setFrozenReport(update(frozenReport, {data: change}));
    }

    if (needsReevaluation && shouldAutoReloadPreview) {
      await reEvaluateReport(newReportData);
    }
  }

  async function reEvaluateReport(newReport) {
    const query = {
      ...report,
      data: newReport,
    };
    await loadUpdatedReport(query);
  }

  function updateName({target: {value}}) {
    const newReport = update(report, {name: {$set: value}});
    setReport(newReport);
    dirtyCheck(newReport);
  }

  function updateDescription(description) {
    track('editDescription', {entity: 'report', entityId: report.id});
    const newReport = update(report, {description: {$set: description}});
    setReport(newReport);
    dirtyCheck(newReport);
  }

  function dirtyCheck(newReport) {
    if (isReportDirty(newReport)) {
      nowDirty(t('report.label'), save);
    } else {
      nowPristine();
    }
  }

  function isReportDirty(newReport = report) {
    return !deepEqual(newReport, originalData);
  }

  function isReportComplete({data: {view, groupBy, visualization}}) {
    return !!(view && groupBy && visualization);
  }

  const loadReport = useCallback(
    function loadReport(params, query = report) {
      return mightFail(
        evaluateReport(query, [], params),
        (response) => {
          setReport(response);
          setFrozenReport(response);
          setServerError(null);
        },
        (serverError) => {
          if (serverError.reportDefinition) {
            setReport(serverError.reportDefinition);
            setServerError(serverError);
          } else {
            setServerError(serverError);
          }
        }
      );
    },
    [mightFail, report, setReport, setFrozenReport]
  );

  const loadUpdatedReport = useCallback(
    async function loadUpdatedReport(query) {
      setReport(query);

      if (isReportComplete(query)) {
        setLoadingReportData(true);
        await loadReport({}, query);
        setLoadingReportData(false);
      }
    },
    [loadReport, setReport]
  );

  function closeConflictModal() {
    updatePromise(null);
    setConflict(null);
  }

  function setLoading(value) {
    setLoadingReportData(value);
  }

  async function toggleAutoPreviewUpdate(shouldReload) {
    if (isReportDirty() && shouldReload) {
      await reEvaluateReport(report.data);
    }
    setShouldAutoReloadPreview(shouldReload);
    sessionStorage.setItem('shouldAutoReloadPreview', shouldReload);
  }

  async function runReportPreviewUpdate() {
    setRunButtonLoading(true);
    await reEvaluateReport(report.data);
    setRunButtonLoading(false);
  }

  function showTable(sectionState) {
    if (sectionState !== 'maximized') {
      setShowReportRenderer(true);
    }
  }

  function handleTableExpand(currentState, newState) {
    track('changeRawDataView', {
      from: currentState,
      to: newState,
      reportType: report.data?.visualization,
    });
    setShowReportRenderer(newState !== 'maximized');
  }

  function handleTableCollapse(currentState, newState) {
    track('changeRawDataView', {
      from: currentState,
      to: newState,
      reportType: report.data?.visualization,
    });
  }

  useEffect(() => {
    // Load report data and set to dirty on initial mount
    if (!isMounted.current && isReportComplete(report) && !report.result) {
      loadUpdatedReport(report);
      nowDirty(t('report.label'), save);
    }

    isMounted.current = true;
  }, [loadUpdatedReport, report, save]);

  if (redirect) {
    history.push(redirect);
  }

  const {name, description, data} = report;

  return (
    <div className="ReportEdit Report">
      <div className="reportHeader">
        <div className="headerTopLine">
          <EntityNameForm
            name={name}
            entity="Report"
            isNew={isNew}
            onChange={updateName}
            onSave={saveAndGoBack}
            onCancel={cancel}
            description={description}
            onDescriptionChange={updateDescription}
          />
          {!shouldAutoReloadPreview && (
            <Button
              kind="primary"
              size="md"
              className="RunPreviewButton"
              disabled={loadingReportData || !isReportComplete(report)}
              onClick={runReportPreviewUpdate}
            >
              {t('report.updateReportPreview.buttonLabel')}
            </Button>
          )}
        </div>
        <div className="headerBottomLine">
          <InstanceCount noInfo report={report} />
          <Toggle
            id="updatePreview"
            className="updatePreview"
            size="sm"
            toggled={shouldAutoReloadPreview}
            onToggle={toggleAutoPreviewUpdate}
            labelText={t('report.updateReportPreview.switchLabel')}
            hideLabel
          />
        </div>
      </div>
      <div className="Report__view" ref={containerRef}>
        <div className="viewsContainer">
          <div className="mainView">
            <div className={classnames('Report__content', {hidden: !showReportRenderer})}>
              <div className="visualization">
                <Visualization report={data} onChange={(change) => updateReport(change, true)} />
                <Configuration
                  type={data.visualization}
                  onChange={updateReport}
                  disabled={loadingReportData}
                  report={report}
                  autoPreviewDisabled={!shouldAutoReloadPreview}
                />
              </div>

              {isReportComplete(report) && <ReportWarnings report={report} />}

              {(shouldAutoReloadPreview || runButtonLoading) && loadingReportData ? (
                <Loading />
              ) : (
                showReportRenderer && (
                  <ReportRenderer
                    error={serverError}
                    report={shouldAutoReloadPreview ? report : frozenReport}
                    updateReport={updateReport}
                    loadReport={loadReport}
                  />
                )
              )}
            </div>
          </div>
          {typeof report.result !== 'undefined' && report.data?.visualization !== 'table' && (
            <CollapsibleContainer
              maxHeight={containerRef.current?.offsetHeight}
              initialState="minimized"
              onTransitionEnd={showTable}
              onExpand={handleTableExpand}
              onCollapse={handleTableCollapse}
              title={t('report.view.rawData')}
            >
              <InstanceViewTable report={shouldAutoReloadPreview ? report : frozenReport} />
            </CollapsibleContainer>
          )}
        </div>
        <ReportControlPanel report={report} updateReport={updateReport} setLoading={setLoading} />
      </div>
      <ConflictModal conflicts={conflict} onClose={closeConflictModal} onConfirm={saveAndGoBack} />
    </div>
  );
}

export default ReportEdit;

function isRearrangementChanged(change) {
  return !!change?.configuration?.tableColumns?.columnOrder;
}
