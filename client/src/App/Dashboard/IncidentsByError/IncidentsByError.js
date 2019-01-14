import React from 'react';
import PropTypes from 'prop-types';

import Collapse from '../Collapse';
import IncidentByError from './IncidentByError';
import {getFilterQueryString} from 'modules/utils/filter';

import * as Styled from './styled';

export default class IncidentsByError extends React.Component {
  static propTypes = {
    incidents: PropTypes.arrayOf(
      PropTypes.shape({
        errorMessage: PropTypes.string.isRequired,
        instancesWithErrorCount: PropTypes.number.isRequired,
        workflows: PropTypes.arrayOf(
          PropTypes.shape({
            bpmnProcessId: PropTypes.string.isRequired,
            errorMessage: PropTypes.string.isRequired,
            instancesWithActiveIncidentsCount: PropTypes.number.isRequired,
            name: PropTypes.string,
            version: PropTypes.number.isRequired,
            workflowId: PropTypes.string.isRequired
          })
        ).isRequired
      })
    )
  };

  renderIncidentsPerWorkflow = (errorMessage, items) => {
    return (
      <ul>
        {items.map(item => {
          const name = item.name || item.bpmnProcessId;
          const query = getFilterQueryString({
            workflow: item.bpmnProcessId,
            version: `${item.version}`,
            errorMessage,
            incidents: true
          });
          const title = `View ${
            item.instancesWithActiveIncidentsCount
          } Instances with error ${item.errorMessage} in version ${
            item.version
          } of Workflow ${name}`;

          return (
            <Styled.VersionLi key={item.workflowId}>
              <Styled.IncidentLink to={`/instances${query}`} title={title}>
                <IncidentByError
                  label={`${name} – Version ${item.version}`}
                  incidentsCount={item.instancesWithActiveIncidentsCount}
                  perUnit
                />
              </Styled.IncidentLink>
            </Styled.VersionLi>
          );
        })}
      </ul>
    );
  };

  renderIncidentByError = item => {
    const query = getFilterQueryString({
      errorMessage: item.errorMessage,
      incidents: true
    });

    const title = `View ${item.instancesWithErrorCount} Instances with error ${
      item.errorMessage
    }`;

    return (
      <Styled.IncidentLink to={`/instances${query}`} title={title}>
        <IncidentByError
          label={`${item.errorMessage}`}
          incidentsCount={item.instancesWithErrorCount}
        />
      </Styled.IncidentLink>
    );
  };

  render() {
    const {incidents} = this.props;
    return (
      <ul>
        {incidents.map((item, index) => {
          const IncidentByErrorComponent = this.renderIncidentByError(item);

          return (
            <Styled.Li
              key={item.errorMessage}
              data-test={`incident-byError-${index}`}
            >
              <Collapse
                content={this.renderIncidentsPerWorkflow(
                  item.errorMessage,
                  item.workflows
                )}
                header={IncidentByErrorComponent}
                buttonTitle={`Expand ${
                  item.instancesWithErrorCount
                } Instances with error "${item.errorMessage}"`}
              />
            </Styled.Li>
          );
        })}
      </ul>
    );
  }
}
