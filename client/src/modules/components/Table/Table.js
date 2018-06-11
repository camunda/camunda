import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default class Table extends React.Component {
  static propTypes = {
    data: PropTypes.array.isRequired,
    config: PropTypes.object
  };

  static defaultProps = {
    config: {}
  };

  getOrder() {
    return this.props.config.order || Object.keys(this.props.data[0]);
  }

  renderHeader() {
    const {config} = this.props;
    if (config.headerLabels) {
      return (
        <Styled.TableHead>
          <Styled.HeaderRow>
            {this.getOrder().map(key => (
              <Styled.HeaderCell key={key}>
                {config.headerLabels[key]}
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
    const order = this.getOrder();

    return data.map((row, idx) => (
      <Styled.BodyRow key={idx}>
        {order.map(key => (
          <Styled.BodyCell key={key}>{row[key]}</Styled.BodyCell>
        ))}
      </Styled.BodyRow>
    ));
  }

  render() {
    return (
      <Styled.Table>
        {this.renderHeader()}

        <tbody>{this.renderBody()}</tbody>
      </Styled.Table>
    );
  }
}
