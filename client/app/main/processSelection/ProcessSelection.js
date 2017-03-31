import {jsx, withSelector, Match, Case, Default, Scope, List, Text, OnEvent} from 'view-utils';
import {LoadingIndicator, DiagramPreview} from 'widgets';
import {loadProcessDefinitions, openDefinition} from './service';
import {isLoaded, runOnce} from 'utils';

export const ProcessSelection = withSelector(() => {
  const template = <div className="process-selection">
    <h3>Select a Process Definition:</h3>
    <LoadingIndicator predicate={isLoading}>
      <Match>
        <Case predicate={areThereNoProcessDefinitions}>
          <div className="no-definitions">
            <span className="indicator glyphicon glyphicon-info-sign"></span>
            <div className="title">No Process Definitions</div>
            <div className="text"><a href="https://github.com/camunda/camunda-optimize/wiki/Installation-guide">Find out how to import your data</a></div>
          </div>
        </Case>
        <Default>
          <Scope selector={getProcessDefinitions}>
            <div className="row">
              <List>
                <div className="col-lg-3 col-md-4 col-sm-6 col-xs-12" style="margin-bottom: 20px;">
                  <div className="process-definition-card">
                    <OnEvent event="click" listener={selectDefinition} />
                    <div className="diagram">
                      <DiagramPreview selector="bpmn20Xml" />
                    </div>
                    <div className="name">
                      <Text property="name" />
                      <span className="version">
                        <span style="color: darkgray;">v</span>
                        <Text property="version" />
                      </span>
                    </div>
                  </div>
                </div>
              </List>
            </div>
          </Scope>
        </Default>
      </Match>
    </LoadingIndicator>
  </div>;

  function selectDefinition({state:{id}}) {
    openDefinition(id);
  }

  function isLoading({processDefinitions}) {
    return !isLoaded(processDefinitions);
  }

  function areThereNoProcessDefinitions({processDefinitions:{data}}) {
    return data.length === 0;
  }

  function getProcessDefinitions({processDefinitions:{data}}) {
    return data;
  }

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, runOnce(loadProcessDefinitions)];
  };
});
