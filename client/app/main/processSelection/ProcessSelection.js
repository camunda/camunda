import React from 'react';
import {LoadingIndicator} from 'widgets/LoadingIndicator.react';
import {loadProcessDefinitions, resetData} from './service';
import {isLoaded} from 'utils';
import {PreviewCard} from './PreviewCard';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export class ProcessSelectionReact extends React.Component {
  render() {
    if (!this.props.processDefinitions) {
      return null;
    }

    return <div className="process-selection">
      <h3>Select a Process Definition:</h3>
      <LoadingIndicator loading={!isLoaded(this.props.processDefinitions)}>
        {isLoaded(this.props.processDefinitions) ? this.getDefinitions() : null}
      </LoadingIndicator>
    </div>;
  }

  getDefinitions() {
    const {data: {list, engineCount}} = this.props.processDefinitions;
    const hasDefinitions = list.length > 0;

    const noDefinitions = <div className="no-definitions">
      <span className="indicator glyphicon glyphicon-info-sign"></span>
      <div className="title">No Process Definitions</div>
      <div className="text"><a href="https://docs.camunda.org/optimize/">Find out how to import your data</a></div>
    </div>;

    const cardList = <div className="row">
      {
        list.map(({current, versions}) =>
          <PreviewCard key={current.id} current={current} versions={versions} engineCount={engineCount} />
        )
      }
    </div>;

    return hasDefinitions ? cardList : noDefinitions;
  }

  componentWillMount() {
    loadProcessDefinitions();
  }

  componentWillUnmount() {
    resetData();
  }
}

export const ProcessSelection = createViewUtilsComponentFromReact('div', ProcessSelectionReact);
