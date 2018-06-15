import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';

import Header from '../Header';
import DiagramPanel from './DiagramPanel';

import * as Styled from './styled';

export default class Instance extends Component {
  static propTypes = {
    match: PropTypes.shape({
      params: PropTypes.shape({
        id: PropTypes.string.isRequired
      }).isRequired
    }).isRequired
  };

  render() {
    const instanceId = this.props.match.params.id;
    const stateIcon = <Styled.IncidentIcon />;

    return (
      <Fragment>
        <Header
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
          detail={
            <Fragment>
              {stateIcon} Instance {instanceId}
            </Fragment>
          }
        />
        <Styled.Instance>
          <Styled.Top>
            <DiagramPanel instanceId={instanceId} stateIcon={stateIcon} />
          </Styled.Top>
          <Styled.Bottom>
            <Panel>
              <Panel.Header foldButtonType="right">
                Instance history
              </Panel.Header>
              <Panel.Body />
            </Panel>
          </Styled.Bottom>
        </Styled.Instance>
      </Fragment>
    );
  }
}
