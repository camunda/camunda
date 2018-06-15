import React from 'react';
import update from 'immutability-helper';

import Panel from 'modules/components/Panel';
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
    entriesPerPage: null,
    selection: {
      list: new Set(),
      isBlacklist: false
    }
  };

  render() {
    return (
      <Panel>
        <Panel.Header>Instances</Panel.Header>
        <Panel.Body>
          <InstancesList
            data={this.state.instances}
            selection={this.state.selection}
            updateEntriesPerPage={entriesPerPage =>
              this.setState({entriesPerPage})
            }
            updateSelection={change => {
              this.setState({selection: update(this.state.selection, change)});
            }}
          />
        </Panel.Body>
        <Panel.Footer>
          <InstancesListFooter
            total={this.props.instancesInFilter}
            perPage={this.state.entriesPerPage}
            firstElement={this.state.firstElement}
            onFirstElementChange={firstElement => this.setState({firstElement})}
          />
        </Panel.Footer>
      </Panel>
    );
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevProps.filter !== this.props.filter) {
      return this.setState({firstElement: 0}, this.loadData);
    }
    if (
      prevProps.filter !== this.props.filter ||
      prevState.firstElement !== this.state.firstElement ||
      prevState.entriesPerPage !== this.state.entriesPerPage
    ) {
      this.loadData();
    }
  }

  loadData = async () => {
    this.setState({
      instances: await getData(
        this.props.filter,
        this.state.firstElement,
        this.state.entriesPerPage
      )
    });
  };
}
