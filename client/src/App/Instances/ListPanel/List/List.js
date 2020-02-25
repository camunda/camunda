/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useContext} from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import Actions from 'modules/components/Actions';
import StateIcon from 'modules/components/StateIcon';
import EmptyMessage from './../../EmptyMessage';

import {EXPAND_STATE} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import ColumnHeader from './ColumnHeader';
import ListContext, {useListContext} from './ListContext';
import BaseSkeleton from './Skeleton';
import * as Styled from './styled';

import InstanceSelectionContext from 'modules/contexts/InstanceSelectionContext';

const {THead, TBody, TH, TR, TD} = Table;

class List extends React.Component {
  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    Overlay: PropTypes.oneOfType([PropTypes.func, PropTypes.bool]),
    onEntriesPerPageChange: PropTypes.func.isRequired,
    isDataLoaded: PropTypes.bool.isRequired,
    filterCount: PropTypes.number,
    filter: PropTypes.object,
    sorting: PropTypes.object,
    onSort: PropTypes.func,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    onActionButtonClick: PropTypes.func,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]),
    rowsToDisplay: PropTypes.number
  };

  constructor(props) {
    super(props);
    this.containerRef = React.createRef();
  }

  componentDidMount() {
    this.recalculateHeight();
  }

  componentDidUpdate(prevProps) {
    const {expandState} = this.props;

    // only call recalculateHeight if the expandedId changes and the pane is not collapsed
    if (
      prevProps.expandState !== expandState &&
      expandState !== EXPAND_STATE.COLLAPSED
    ) {
      this.recalculateHeight();
    }
  }

  recalculateHeight() {
    if (this.containerRef.current) {
      const rows = ~~(this.containerRef.current.clientHeight / 38) - 1;
      this.props.onEntriesPerPageChange(rows);
    }
  }

  handleActionButtonClick = instance => {
    this.props.onActionButtonClick(instance);
  };

  render() {
    return (
      <Styled.List>
        <Styled.TableContainer ref={this.containerRef}>
          {this.props.Overlay && this.props.Overlay()}

          <ListContext.Provider
            value={{
              data: this.props.data,
              filter: this.props.filter,
              sorting: this.props.sorting,
              onSort: this.props.onSort,
              rowsToDisplay: this.props.rowsToDisplay,
              isDataLoaded: this.props.isDataLoaded,
              handleActionButtonClick: this.handleActionButtonClick
            }}
          >
            <Table>{this.props.children}</Table>
          </ListContext.Provider>
        </Styled.TableContainer>
      </Styled.List>
    );
  }
}

export default List;

const Skeleton = function(props) {
  const {rowsToDisplay} = useListContext();
  return <BaseSkeleton {...props} rowsToDisplay={rowsToDisplay} />;
};

const Message = function({message}) {
  return (
    <TBody>
      <Styled.EmptyTR>
        <TD colSpan={6}>
          <EmptyMessage
            message={message}
            data-test="empty-message-instances-list"
          />
        </TD>
      </Styled.EmptyTR>
    </TBody>
  );
};

Message.propTypes = {
  message: PropTypes.string
};

const Body = function(props) {
  const {data, rowsToDisplay, handleActionButtonClick} = useListContext();
  const {isInstanceChecked, handleCheckInstance} = useContext(
    InstanceSelectionContext
  );

  return (
    <TBody {...props}>
      {data.slice(0, rowsToDisplay).map((instance, idx) => {
        const isSelected = isInstanceChecked(instance.id);
        return (
          <TR key={idx}>
            <TD>
              <Styled.Cell>
                <Styled.SelectionStatusIndicator selected={isSelected} />
                <Checkbox
                  type="selection"
                  isChecked={isSelected}
                  onChange={handleCheckInstance(instance.id)}
                  title={`Select instance ${instance.id}`}
                />

                <StateIcon state={instance.state} />
                <Styled.WorkflowName>
                  {getWorkflowName(instance)}
                </Styled.WorkflowName>
              </Styled.Cell>
            </TD>
            <TD>
              <Styled.InstanceAnchor
                to={`/instances/${instance.id}`}
                title={`View instance ${instance.id}`}
              >
                {instance.id}
              </Styled.InstanceAnchor>
            </TD>
            <TD>{`Version ${instance.workflowVersion}`}</TD>
            <TD>{formatDate(instance.startDate)}</TD>
            <TD>{formatDate(instance.endDate)}</TD>
            <TD>
              <Actions
                instance={instance}
                selected={isSelected}
                onButtonClick={() => handleActionButtonClick(instance)}
              />
            </TD>
          </TR>
        );
      })}
    </TBody>
  );
};

const Header = function(props) {
  const {data, filter, sorting, onSort, isDataLoaded} = useListContext();
  const {handleCheckAll, isAllChecked} = useContext(InstanceSelectionContext);

  const isListEmpty = !isDataLoaded || data.length === 0;
  const listHasFinishedInstances = filter.canceled || filter.completed;
  return (
    <THead {...props}>
      <Styled.TR>
        <TH>
          <React.Fragment>
            <Styled.CheckAll>
              <Checkbox
                disabled={isListEmpty}
                isChecked={isAllChecked}
                onChange={handleCheckAll}
                title="Select all instances"
              />
            </Styled.CheckAll>
            <ColumnHeader
              disabled={isListEmpty}
              onSort={onSort}
              label="Workflow"
              sortKey="workflowName"
              sorting={sorting}
            />
          </React.Fragment>
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Instance Id"
            onSort={onSort}
            sortKey="id"
            sorting={sorting}
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Version"
            onSort={onSort}
            sortKey="workflowVersion"
            sorting={sorting}
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Start Time"
            onSort={onSort}
            sortKey="startDate"
            sorting={sorting}
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty || !listHasFinishedInstances}
            label="End Time"
            onSort={onSort}
            sortKey="endDate"
            sorting={sorting}
          />
        </TH>
        <Styled.ActionsTH>
          <ColumnHeader disabled={isListEmpty} label="Operations" />
        </Styled.ActionsTH>
      </Styled.TR>
    </THead>
  );
};

List.Message = Message;
List.Body = Body;
List.Header = Header;
List.Skeleton = Skeleton;
