import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Table extends React.Component {
  static propTypes = {
    data: PropTypes.array.isRequired,
    headers: PropTypes.object.isRequired,
    config: PropTypes.object,
    handleSorting: PropTypes.func
  };

  static defaultProps = {
    config: {isSortable: {}, sorting: {sortBy: null, sortOrder: null}},
    handleSorting: () => {}
  };

  renderHeader() {
    const {
      headers,
      config: {
        isSortable,
        sorting: {sortBy, sortOrder}
      },
      handleSorting
    } = this.props;

    return (
      <Styled.TableHead>
        <Styled.HeaderRow>
          {Object.entries(headers).map(([key, headerLabel]) => (
            <Styled.HeaderCell key={key}>
              {headerLabel}
              {isSortable[key] && (
                <Styled.SortIcon
                  sortOrder={sortBy === key ? sortOrder : null}
                  onClick={() => handleSorting(key)}
                />
              )}
            </Styled.HeaderCell>
          ))}
        </Styled.HeaderRow>
      </Styled.TableHead>
    );
  }

  renderBody() {
    const selectionCheck = this.props.config.selectionCheck || (() => false);

    return (
      <tbody>
        {this.props.data.map((row, idx) => (
          <Styled.BodyRow key={idx} selected={selectionCheck(row.data)}>
            {Object.keys(this.props.headers).map(key => (
              <Styled.BodyCell key={key}>{row.view[key]}</Styled.BodyCell>
            ))}
          </Styled.BodyRow>
        ))}
      </tbody>
    );
  }

  render() {
    return (
      <Styled.Table {...this.props}>
        {this.renderHeader()}
        {this.renderBody()}
      </Styled.Table>
    );
  }
}
