/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {withRouter} from 'react-router';
import classnames from 'classnames';

import {
  ReportRenderer,
  LoadingIndicator,
  EntityName,
  ReportDetails,
  InstanceCount,
} from 'components';
import {withErrorHandling} from 'HOC';
import deepEqual from 'fast-deep-equal';
import {track} from 'tracking';

import {themed} from 'theme';

import './OptimizeReportTile.scss';

export class OptimizeReportTile extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loading: true,
      data: undefined,
      error: null,
      lastParams: {},
    };
  }

  async componentDidMount() {
    await this.loadInitialTile();
  }

  componentDidUpdate(prevProps) {
    if (
      !deepEqual(prevProps.tile, this.props.tile) ||
      !deepEqual(prevProps.filter, this.props.filter)
    ) {
      this.loadInitialTile();
    }
  }

  loadInitialTile = async () => {
    this.setState({loading: true});
    await this.loadTile({});
    this.setState({loading: false});
  };

  loadTile = (params) => {
    this.setState({lastParams: params});
    return new Promise((resolve) => {
      this.props.mightFail(
        this.props.loadTile(
          this.props.tile.id ?? this.props.tile.report,
          this.props.filter,
          params
        ),
        (data) => this.setState({data, error: null}, resolve),
        (error) => {
          this.setState(
            {
              data: error.reportDefinition,
              error,
            },
            resolve
          );
        }
      );
    });
  };

  refreshTile = () => this.loadTile(this.state.lastParams);

  exitDarkmode = () => {
    if (this.props.theme === 'dark') {
      this.props.toggleTheme();
    }
  };

  render() {
    const {loading, data, error} = this.state;

    if (loading) {
      return <LoadingIndicator />;
    }

    const {
      disableNameLink,
      customizeTileLink = (id) => `report/${id}/`,
      filter,
      children = () => {},
    } = this.props;

    const tileLink = customizeTileLink(data?.id);

    let tileProps = {
      className: 'OptimizeReportTile DashboardTile__wrapper',
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
            this.props.history.push(tileLink);
          }
        },
      };
    }

    return (
      <div {...tileProps}>
        {data && (
          <div className="titleBar" tabIndex="-1">
            <EntityName
              linkTo={!disableNameLink && tileLink}
              details={<ReportDetails report={data} />}
              onClick={() => {
                track('drillDownToReport');
              }}
            >
              {data.name}
            </EntityName>
            <InstanceCount report={data} additionalFilter={filter} useIcon="filter" showHeader />
          </div>
        )}
        <div className="visualization">
          <ReportRenderer
            error={error}
            report={data}
            context="dashboard"
            loadReport={this.loadTile}
          />
        </div>
        {children({loadTileData: this.refreshTile})}
      </div>
    );
  }
}

export default themed(withErrorHandling(withRouter(OptimizeReportTile)));
