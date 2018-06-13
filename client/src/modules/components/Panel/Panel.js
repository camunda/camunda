import React, {Component} from 'react';
import PropTypes from 'prop-types';
import PanelHeader from './PanelHeader';
import PanelFooter from './PanelFooter';

import * as Styled from './styled.js';

class Panel extends Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  render() {
    const {children} = this.props;
    return (
      <Styled.Panel>
        <div>{children}</div>
      </Styled.Panel>
    );
  }
}

Panel.Footer = PanelFooter;
Panel.Header = PanelHeader;

export default Panel;
