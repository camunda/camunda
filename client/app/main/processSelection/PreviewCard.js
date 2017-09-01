import {
  jsx, OnEvent, Match, Case, Default, Scope, List,
  Text, Class, isFalsy, createReferenceComponent, Attribute,
  createStateComponent, withSelector
} from 'view-utils';
import {openDefinition, setVersionForProcess} from './service';
import {createDiagramPreview} from 'widgets';

export const PreviewCard = withSelector(() => {
  return (node, eventsBus) => {
    const State = createStateComponent();
    const DiagramPreview = createDiagramPreview();
    const Reference = createReferenceComponent();
    const template = <div className="col-lg-3 col-md-4 col-sm-6 col-xs-12" style="margin-bottom: 20px;">
      <State>
        <div className="process-definition-card">
          <div className="diagram">
            <Class selector="bpmn20Xml" className="no-xml" predicate={isFalsy} />
            <OnEvent event="click" listener={selectDefinition} />
            <DiagramPreview selector="bpmn20Xml" />
          </div>
          <Match>
            <Case predicate={state => state.engineCount > 1}>
              <div className="engine">
                <Text property="engine" />
              </div>
            </Case>
          </Match>
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
                      <Reference name="version-select" />
                      <OnEvent event="change" listener={selectDefinitionVersion} />
                      <List>
                        <option>
                          <Attribute attribute="value" selector="version" />
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
      </State>
    </div>;

    function hasOnlyOneVersion({versions}) {
      return versions.length === 1;
    }

    function selectDefinitionVersion({state, event: {target}}) {
      const {id: previousId} = State.getState();
      const selected = state[target.selectedIndex];

      setVersionForProcess(previousId, selected);
      DiagramPreview.setLoading(true);
    }

    function selectDefinition({state:{id, bpmn20Xml}}) {
      if (bpmn20Xml) {
        openDefinition(id);
      }
    }

    return [
      template(node, eventsBus),
      ({version}) => {
        const versionSelect = Reference.getNode('version-select');

        if (versionSelect) {
          versionSelect.value = version;
        }
      }
    ];
  };
});
