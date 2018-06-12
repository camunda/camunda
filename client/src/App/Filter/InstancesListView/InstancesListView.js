import React from 'react';
import {Panel} from '../Panel';
import {PanelHeader} from '../PanelHeader';
import {PanelFooter} from '../PanelFooter';
import InstancesList from './InstancesList';

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
    instances: generateData(20),
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
        <PanelFooter>
          Displaying page 1 /{' '}
          {Math.ceil(this.props.instancesInFilter / this.state.entriesPerPage)}
        </PanelFooter>
      </Panel>
    );
  }
}

function generateData(number) {
  const data = [];
  for (let i = 0; i < number; i++) {
    data.push({
      id: i,
      name: 'Instance ' + i
    });
  }
  return data;
}
