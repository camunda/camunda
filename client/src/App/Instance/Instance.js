import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Copyright from 'modules/components/Copyright';
import InstanceDetail from './InstanceDetail';

import Header from '../Header';
import DiagramPanel from './DiagramPanel';

import * as Styled from './styled';
import * as api from './api';

export default class Instance extends Component {
  static propTypes = {
    match: PropTypes.shape({
      params: PropTypes.shape({
        id: PropTypes.string.isRequired
      }).isRequired
    }).isRequired
  };

  state = {
    instance: null,
    loaded: false
  };

  fetchWorkflowInstance = async id => {
    const data = await api.workflowInstance(id);
    return data;
  };

  async componentDidMount() {
    const id = this.props.match.params.id;
    const instance = await this.fetchWorkflowInstance(id);

    this.setState({
      loaded: true,
      instance
    });
  }

  render() {
    if (!this.state.loaded) {
      return 'Loading';
    }

    return (
      <Fragment>
        <Header
          instances={14576}
          filters={9263}
          selections={24}
          incidents={328}
          detail={<InstanceDetail instance={this.state.instance} />}
        />
        <Styled.Instance>
          <Styled.Top>
            <DiagramPanel instance={this.state.instance} />
          </Styled.Top>
          <Styled.Bottom>
            <Panel>
              <Panel.Header foldButtonType="right">
                Instance history
              </Panel.Header>
              <Panel.Body />
              <Styled.PanelFooter>
                <Copyright />
              </Styled.PanelFooter>
            </Panel>
          </Styled.Bottom>
        </Styled.Instance>
      </Fragment>
    );
  }
}
