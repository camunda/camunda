import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';
import InstanceDetail from './InstanceDetail';

import Header from '../Header';
import DiagramPanel from './DiagramPanel';

import * as Styled from './styled';
import * as api from 'modules/api/instances';

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

  async componentDidMount() {
    const id = this.props.match.params.id;
    const instance = await api.fetchWorkflowInstance(id);

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
          <SplitPane>
            <DiagramPanel instance={this.state.instance} />
            <SplitPane.Pane>
              <SplitPane.Pane.Header>Instance history</SplitPane.Pane.Header>
              <SplitPane.Pane.Body />
              <Styled.PaneFooter>
                <Copyright />
              </Styled.PaneFooter>
            </SplitPane.Pane>
          </SplitPane>
        </Styled.Instance>
      </Fragment>
    );
  }
}
