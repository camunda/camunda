/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {useHistory} from 'react-router-dom';
import classnames from 'classnames';
import {Filter} from '@carbon/icons-react';
import deepEqual from 'fast-deep-equal';

import {
  ReportRenderer,
  Loading,
  EntityName,
  ReportDetails,
  InstanceCount,
  Popover,
} from 'components';
import {useErrorHandling} from 'hooks';
import {track} from 'tracking';
import {t} from 'translation';

import './OptimizeReportTile.scss';

export default function OptimizeReportTile({
  tile,
  loadTile,
  disableNameLink,
  customizeTileLink = (id) => `report/${id}/`,
  filter,
  children,
}) {
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState();
  const [error, setError] = useState(null);
  const [lastParams, setLastParams] = useState({});
  const {mightFail} = useErrorHandling();
  const history = useHistory();
  const prevTile = useRef(tile);
  const prevFilter = useRef(filter);

  const loadTileData = useCallback(
    (params) => {
      setLastParams(params);
      return new Promise((resolve) => {
        mightFail(
          loadTile(tile.id ?? tile.report, filter, params),
          (data) => {
            setData(data);
            setError(null);
            resolve();
          },
          (error) => {
            setData(error.reportDefinition);
            setError(error);
            resolve();
          }
        );
      });
    },
    [mightFail, loadTile, tile, filter]
  );

  const loadInitialTile = useCallback(async () => {
    setLoading(true);
    await loadTileData({});
    setLoading(false);
  }, [loadTileData]);

  useEffect(() => {
    if (!deepEqual(tile, prevTile.current) || !deepEqual(filter, prevFilter.current)) {
      prevTile.current = tile;
      prevFilter.current = filter;
      loadInitialTile();
    }
  }, [tile, filter, loadInitialTile]);

  useEffect(() => {
    loadInitialTile();
    // When we put loadTileData into the dependency array,
    // it causes weird behavior when you click on a tile the first time.
    // For example clicking on copy button will not work with the first click.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) {
    return <Loading />;
  }

  const refreshTile = () => loadTileData(lastParams);
  const tileLink = customizeTileLink(data?.id || tile?.id);
  let tileProps = {
    className: 'OptimizeReportTile DashboardTile',
  };

  if (!disableNameLink) {
    const visualization = data?.data?.visualization;
    const canOnlyClickTitle = visualization === 'pie' || visualization === 'table';
    tileProps = {
      role: 'link',
      className: classnames(tileProps.className, {canOnlyClickTitle}),
      onClick: (evt) => {
        if (
          !evt.target.closest('.DropdownOption') &&
          !evt.target.closest('a') &&
          !evt.target.closest('button') &&
          !(evt.target.closest('.visualization') && canOnlyClickTitle)
        ) {
          track('drillDownToReport');
          history.push(tileLink);
        }
      },
    };
  }

  return (
    <div {...tileProps}>
      {data && (
        <div className="titleBar" tabIndex={-1}>
          <EntityName
            linkTo={!disableNameLink && tileLink}
            details={<ReportDetails report={data} />}
            onClick={() => {
              track('drillDownToReport');
            }}
            name={data.name}
          />
          <InstanceCount
            key="instanceCount"
            trigger={
              <Popover.Button
                size="sm"
                kind="ghost"
                hasIconOnly
                iconDescription={t('common.filter.label').toString()}
                renderIcon={Filter}
                tooltipPosition="bottom"
              />
            }
            report={data}
            additionalFilter={filter}
            showHeader
          />
        </div>
      )}
      <div className="visualization">
        <ReportRenderer error={error} report={data} context="dashboard" loadReport={loadTileData} />
      </div>
      {children?.({loadTileData: refreshTile})}
    </div>
  );
}

OptimizeReportTile.isTileOfType = function (tile) {
  return tile.type === 'optimize_report';
};
