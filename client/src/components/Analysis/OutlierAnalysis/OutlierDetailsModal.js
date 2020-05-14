/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, ButtonGroup, Button} from 'components';

import './OutlierDetailsModal.scss';
import {t} from 'translation';
import DurationChart from './DurationChart';
import VariablesTable from './VariablesTable';
import InstancesButton from './InstancesButton';

export default class OutlierDetailsModal extends React.Component {
  state = {
    tableView: false,
  };

  render() {
    const {tableView} = this.state;
    const {id, name, higherOutlier, data, totalCount} = this.props.selectedNode;
    return (
      <Modal open size="large" onClose={this.props.onClose} className="OutlierDetailsModal">
        <Modal.Header>{t('analysis.outlier.detailsModal.title', {name})}</Modal.Header>
        <Modal.Content>
          <ButtonGroup>
            <Button onClick={() => this.setState({tableView: false})} active={!tableView}>
              {t('analysis.outlier.detailsModal.durationChart')}
            </Button>
            <Button onClick={() => this.setState({tableView: true})} active={tableView}>
              {t('analysis.outlier.detailsModal.variablesTable')}
            </Button>
          </ButtonGroup>
          {!tableView && (
            <>
              <p className="description">
                {t(
                  `analysis.outlier.tooltipText.${
                    higherOutlier.count === 1 ? 'singular' : 'plural'
                  }`,
                  {
                    count: higherOutlier.count,
                    percentage: Math.round(higherOutlier.relation * 100),
                  }
                )}
                <InstancesButton
                  id={id}
                  name={name}
                  value={higherOutlier.boundValue}
                  config={this.props.config}
                />
              </p>
              <DurationChart data={data} />
            </>
          )}
          {tableView && (
            <>
              <p className="description">
                {t('analysis.outlier.totalInstances')}: {totalCount}
              </p>
              <VariablesTable config={this.props.config} selectedNode={this.props.selectedNode} />
            </>
          )}
        </Modal.Content>
      </Modal>
    );
  }
}
