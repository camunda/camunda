import React, {Component, Fragment} from 'react';
import Panel from 'modules/components/Panel';
import Header from '../Header';
import Diagram from './Diagram';

import * as Styled from './styled';

export default class Instance extends Component {
  render() {
    const instaceId = this.props.match.params.id;
    return (
      <Fragment>
        <Header
          active="detail"
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
          detail={instaceId}
        />
        <Styled.Instance>
          <Styled.Top>
            <Panel>
              <Panel.Header headline="Process_definition_name" />
              <Diagram />
            </Panel>
          </Styled.Top>
          <Styled.Bottom>
            <Panel>
              <Panel.Header
                headline="Instance history"
                foldButtonType="right"
              />
            </Panel>
          </Styled.Bottom>
        </Styled.Instance>
      </Fragment>
    );
  }
}
