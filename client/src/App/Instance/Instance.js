import React, {Component, Fragment} from 'react';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';

import Header from '../Header';

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
              <Styled.DiagramPanelHeader>
                <span>
                  <Styled.IncidentIcon /> Process_definition_name
                </span>
              </Styled.DiagramPanelHeader>
              <Styled.IncidentMessage>
                <strong>Incident:</strong> Error Message goes here
              </Styled.IncidentMessage>
              <Diagram />
            </Panel>
          </Styled.Top>
          <Styled.Bottom>
            <Panel>
              <Panel.Header>Instance history</Panel.Header>
            </Panel>
          </Styled.Bottom>
        </Styled.Instance>
      </Fragment>
    );
  }
}
