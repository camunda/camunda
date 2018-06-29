import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Table extends React.Component {
  static propTypes = {
    data: PropTypes.array.isRequired,
    config: PropTypes.object,
    sorting: PropTypes.object,
    handleSorting: PropTypes.func
  };

  static defaultProps = {
    config: {}
  };

  getOrder() {
    return this.props.config.order || Object.keys(this.props.data[0]);
  }

  renderHeader() {
    const {
      config: {headerLabels, sortable = {}, sorting},
      handleSorting
    } = this.props;

    if (headerLabels) {
      return (
        <Styled.TableHead>
          <Styled.HeaderRow>
            {this.getOrder().map(key => (
              <Styled.HeaderCell key={key}>
                {headerLabels[key]}
                {sortable[key] && (
                  <Styled.SortIcon
                    order={sorting[key]}
                    onClick={() => handleSorting(key)}
                  />
                )}
              </Styled.HeaderCell>
            ))}
          </Styled.HeaderRow>
        </Styled.TableHead>
      );
    }

    return null;
  }

  renderBody() {
    const {data} = this.props;
    if (!data || data.length === 0) {
      return null;
    }

    const order = this.getOrder();
    const selectionCheck = this.props.config.selectionCheck || (() => false);

    return data.map((row, idx) => (
      <Styled.BodyRow key={idx} selected={selectionCheck(row)}>
        {order.map(key => (
          <Styled.BodyCell key={key}>{row[key]}</Styled.BodyCell>
        ))}
      </Styled.BodyRow>
    ));
  }

  render() {
    return (
      <Styled.Table {...this.props}>
        {this.renderHeader()}

        <tbody>{this.renderBody()}</tbody>
      </Styled.Table>
    );
  }
}
