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
    config: {},
    handleSorting: () => {}
  };

  renderHeader() {
    const {
      headers,
      config: {isSortable = {}, sortBy},
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
                  order={sortBy[key]}
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
          <Styled.BodyRow key={idx} selected={selectionCheck(row)}>
            {Object.keys(this.props.headers).map(key => (
              <Styled.BodyCell key={key}>{row[key]}</Styled.BodyCell>
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
