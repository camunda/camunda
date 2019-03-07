/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Table from 'modules/components/Table';
import Button from 'modules/components/Button';
import {IncidentAction} from 'modules/components/Actions';
import ColumnHeader from '../../../Instances/ListView/List/ColumnHeader';
import Modal from 'modules/components/Modal';
import {formatDate} from 'modules/utils/date';
import * as Styled from './styled';
const {THead, TBody, TH, TR, TD} = Table;

export default class IncidentsTable extends React.Component {
  static propTypes = {
    incidents: PropTypes.array.isRequired,
    onIncidentOperation: PropTypes.func.isRequired,
    instanceId: PropTypes.string.isRequired,
    forceSpinner: PropTypes.bool
  };

  static defaultProps = {
    forceSpinner: false
  };

  state = {isModalVisibile: false};

  toggleModal = ({content, title}) => {
    this.setState(prevState => ({
      isModalVisibile: !prevState.isModalVisibile,
      modalContent: content ? content : null,
      modalTitle: title ? title : null
    }));
  };

  renderModal = message => {
    return (
      <Modal onModalClose={this.toggleModal}>
        <Modal.Header>{this.state.modalTitle}</Modal.Header>
        <Modal.Body>
          <Modal.BodyText>{this.state.modalContent}</Modal.BodyText>
        </Modal.Body>
        <Modal.Footer>
          <Modal.PrimaryButton title="Close Modal" onClick={this.toggleModal}>
            Close
          </Modal.PrimaryButton>
        </Modal.Footer>
      </Modal>
    );
  };

  handleActionButtonClick = () => {
    this.props.onIncidentOperation();
  };

  render() {
    const {incidents} = this.props;
    return (
      <>
        <Table>
          <THead>
            <TR>
              <Styled.FirstTH>
                <ColumnHeader
                  sortKey="1"
                  label="Incident Type"
                  sorting={{sortBy: ''}}
                  disabled
                />
              </Styled.FirstTH>
              <TH>
                <ColumnHeader
                  sortKey="1"
                  label="Flow Node"
                  sorting={{sortBy: ''}}
                  disabled
                />
              </TH>
              <TH>
                <ColumnHeader
                  sortKey="1"
                  label="Job Id"
                  sorting={{sortBy: ''}}
                  disabled
                />
              </TH>
              <TH>
                <ColumnHeader
                  sortKey="1"
                  label="Creation Time"
                  sorting={{sortBy: ''}}
                  disabled
                />
              </TH>
              <TH>
                <ColumnHeader label="Error Message" />
              </TH>
              <TH>
                <ColumnHeader label="Actions" />
              </TH>
            </TR>
          </THead>
          <TBody>
            {incidents.map((incident, index) => {
              return (
                <TR key={incident.id}>
                  <TD>
                    <Styled.FirstCell index={index + 1}>
                      <Styled.Index>{index}</Styled.Index>
                      {incident.errorType}
                    </Styled.FirstCell>
                  </TD>
                  <TD>{incident.flowNodeName}</TD>
                  <TD>{incident.jobId || '--'}</TD>
                  <TD>{formatDate(incident.creationTime)}</TD>
                  <TD>
                    <Styled.Flex>
                      <Styled.ErrorMessageCell>
                        {incident.errorMessage}
                      </Styled.ErrorMessageCell>
                      {incident.errorMessage.length >= 58 && (
                        <Button
                          size="small"
                          onClick={this.toggleModal.bind(this, {
                            content: incident.errorMessage,
                            title: `Flow Node ${incident.flowNodeName} Error`
                          })}
                        >
                          More...
                        </Button>
                      )}
                    </Styled.Flex>
                  </TD>
                  <TD>
                    <IncidentAction
                      instanceId={this.props.instanceId}
                      onButtonClick={this.handleActionButtonClick}
                      incident={incident}
                      showSpinner={
                        this.props.forceSpinner || incident.hasActiveOperation
                      }
                    />
                  </TD>
                </TR>
              );
            })}
          </TBody>
        </Table>
        {this.state.isModalVisibile && this.renderModal()}
      </>
    );
  }
}
