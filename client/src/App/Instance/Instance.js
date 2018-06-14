import React, {Component, Fragment} from 'react';

import Panel from 'modules/components/Panel';

import Header from '../Header';
import DiagramPanel from './DiagramPanel';

import * as Styled from './styled';

export default class Instance extends Component {
  render() {
    const instanceId = this.props.match.params.id;
    return (
      <Fragment>
        <Header
          active="detail"
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
          detail={instanceId}
        />
        <Styled.Instance>
          <Styled.Top>
            <DiagramPanel instanceId={instanceId} />
          </Styled.Top>
          <Styled.Bottom>
            <Panel>
              <Panel.Header foldButtonType="right">
                Instance history
              </Panel.Header>
              <Panel.Body> Body of bottom panel without footer</Panel.Body>
            </Panel>
          </Styled.Bottom>
        </Styled.Instance>
      </Fragment>
    );
  }
}
