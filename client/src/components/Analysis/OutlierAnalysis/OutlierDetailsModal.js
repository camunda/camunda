/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Modal, ButtonGroup, Button} from 'components';

import './OutlierDetailsModal.scss';
import {t} from 'translation';
import DurationChart from './DurationChart';
import VariablesTable from './VariablesTable';

export default class OutlierDetailsModal extends Component {
  state = {
    tableView: false
  };

  render() {
    const {tableView} = this.state;
    const {name, higherOutlier, data} = this.props.selectedNode;
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
                {t('analysis.outlier.tooltipText', {
                  count: higherOutlier.count,
                  instance: t(
                    `analysis.outlier.tooltip.instance.label${
                      higherOutlier.count === 1 ? '' : '-plural'
                    }`
                  ),
                  percentage: Math.round(higherOutlier.relation * 100)
                })}
              </p>
              <DurationChart data={data} />
            </>
          )}
          {tableView && (
            <VariablesTable config={this.props.config} selectedNode={this.props.selectedNode} />
          )}
        </Modal.Content>
      </Modal>
    );
  }
}
