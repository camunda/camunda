import React from 'react';
import {Panel} from 'App/Filter/Panel';
import {PanelHeader} from 'App/Filter/PanelHeader';
import InstancesList from './InstancesList';
import InstancesListFooter from './InstancesListFooter';

import {getData} from './api';

/**
 * This component is responsible for the handling the current state of the process instance list view and synchronize this with the table and the Panel footer.
 * It is also responsible for loading the list of instances.
 * As props it gets the
 *  - number of total instances
 *  - number of instances in the current filter
 *  - number of selections
 *  - number of incidents
 *  - filter
 * It propagates changes to
 *  - Selections
 * up to the Filter component, which can then synchronize this with the Header and Selection sections as well as use the firstListItem .
 */
export default class InstancesListView extends React.Component {
  state = {
    firstElement: 0,
    instances: null,
    entriesPerPage: null
  };

  render() {
    return (
      <Panel>
        <PanelHeader headline="Instances" />
        <InstancesList
          data={this.state.instances}
          updateEntriesPerPage={entriesPerPage =>
            this.setState({entriesPerPage})
          }
        />
        <InstancesListFooter
          total={this.props.instancesInFilter}
          perPage={this.state.entriesPerPage}
          firstElement={this.state.firstElement}
          changePage={newPage => this.setState({firstElement: newPage})}
        />
      </Panel>
    );
  }

  componentDidMount() {
    this.loadData();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.filter !== this.props.filter) {
      this.loadData();
    }
  }

  loadData = async () => {
    this.setState({instances: await getData()});
  };
}
