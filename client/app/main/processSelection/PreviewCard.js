import {
  jsx, OnEvent, Match, Case, Default, Scope, List,
  Text, Class, isFalsy, createReferenceComponent, Attribute,
  createStateComponent
} from 'view-utils';
import {openDefinition, setVersionForProcess} from './service';
import {createDiagramPreview} from 'widgets';

export function PreviewCard() {
  return (node, eventsBus) => {
    const State = createStateComponent();
    const DiagramPreview = createDiagramPreview();
    const Reference = createReferenceComponent();
    const template = <div className="col-lg-3 col-md-4 col-sm-6 col-xs-12" style="margin-bottom: 20px;">
      <State>
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
        </Scope>
      </State>
    </div>;

    function hasOnlyOneVersion({versions}) {
      return versions.length === 1;
    }

    function selectDefinitionVersion({state, event: {target}}) {
      const {current: {id: previousId}} = State.getState();
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
      ({current: {version}}) => {
        const versionSelect = Reference.getNode('version-select');

        if (versionSelect) {
          versionSelect.value = version;
        }
      }
    ];
  };
}
