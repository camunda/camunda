import React, {Component} from 'react';
import {StatsPanel} from './StatsPanel';

import * as Styled from './styled.js';

class Dashboard extends Component {
  render() {
    return (
      <Styled.Dashboard>
        <StatsPanel />
      </Styled.Dashboard>
    );
  }
}

export default Dashboard;
