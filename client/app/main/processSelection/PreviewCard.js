import {jsx, OnEvent, Match, Case, Default, Scope, List, Text} from 'view-utils';
import {openDefinition, setVersionForProcess} from './service';
import {DiagramPreview} from 'widgets';

export function PreviewCard() {
  const template = <div className="col-lg-3 col-md-4 col-sm-6 col-xs-12" style="margin-bottom: 20px;">
    <div className="process-definition-card">
      <div className="diagram">
        <OnEvent event="click" listener={selectDefinition} />
        <DiagramPreview selector="bpmn20Xml" />
      </div>
      <div className="name-box">
        <span className="name">
          <Text property="name" />
        </span>
        <span className="version">
          <Match>
            <Case predicate={hasOnlyOneVersion}>
              v<Text property="version" />
            </Case>
            <Default>
              <Scope selector="versions">
                <select>
                  <OnEvent event="change" listener={selectDefinitionVersion} />
                  <List>
                    <option>
                      v<Text property="version" />
                    </option>
                  </List>
                </select>
              </Scope>
            </Default>
          </Match>
        </span>
      </div>
    </div>
  </div>;

  function hasOnlyOneVersion({versions}) {
    return versions.length === 1;
  }

  function selectDefinitionVersion({state, event: {target: {selectedIndex}}}) {
    const {key, version} = state[selectedIndex];

    setVersionForProcess(key, version);
  }

  function selectDefinition({state:{id}}) {
    openDefinition(id);
  }

  return template;
}
