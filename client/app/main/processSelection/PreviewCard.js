import {jsx, OnEvent, Match, Case, Default, Scope, List, Text, Class, isFalsy} from 'view-utils';
import {openDefinition, setVersionForProcess} from './service';
import {createDiagramPreview} from 'widgets';

export function PreviewCard() {
  return (node, eventsBus) => {
    const DiagramPreview = createDiagramPreview();

    const template = <div className="col-lg-3 col-md-4 col-sm-6 col-xs-12" style="margin-bottom: 20px;">
      <Scope selector={({current, versions}) => ({...current, versions})}>
        <div className="process-definition-card">
          <div className="diagram">
            <Class selector="bpmn20Xml" className="no-xml" predicate={isFalsy} />
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
      </Scope>
    </div>;

    function hasOnlyOneVersion({versions}) {
      return versions.length === 1;
    }

    function selectDefinitionVersion({state, event: {target: {selectedIndex}}}) {
      const selected = state[selectedIndex];

      setVersionForProcess(selected);
      DiagramPreview.setLoading(true);
    }

    function selectDefinition({state:{id, bpmn20Xml}}) {
      if (bpmn20Xml) {
        openDefinition(id);
      }
    }

    return template(node, eventsBus);
  };
}
