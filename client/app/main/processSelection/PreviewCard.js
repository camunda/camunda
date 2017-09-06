import React from 'react';
import {openDefinition, setVersionForProcess} from './service';
import {DiagramPreview} from 'widgets';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export class PreviewCardReact extends React.PureComponent {
  constructor(props) {
    super(props);

    this.state = {
      loading: true
    };
  }

  render() {
    const {versions = [], current = {}} = this.props;

    const engine = <div className="engine">
      {current.engine}
    </div>;

    const singleVersion = <span className="version">
      v{current.version}
    </span>;

    const versionSelect = <span className="version">
      <select onChange={this.selectDefinitionVersion} value={current.version}>
        {
          versions.map(version =>
            <option value={version.version} key={version.id}>
              v{version.version}
            </option>
          )
        }
      </select>
    </span>;

    const diagram = <DiagramPreview diagram={current.bpmn20Xml} loading={this.state.loading} onLoaded={this.onLoaded} />;

    return <div className="col-lg-3 col-md-4 col-sm-6 col-xs-12" style={{marginBottom: '20px'}}>
      <div className="process-definition-card">

        <div className={'diagram ' + (current.bpmn20Xml ? '' : 'no-xml')} onClick={this.selectDefinition}>
          {current.bpmn20Xml ? diagram : ''}
        </div>

        {this.props.engineCount > 1 ? engine : ''}

        <div className="name-box">
          <span className="name">
            {current.name}
          </span>

          {versions.length > 1 ? versionSelect : singleVersion}
        </div>
      </div>
    </div>;
  }

  selectDefinition = () => {
    if (this.props.current.bpmn20Xml) {
      openDefinition(this.props.current.id);
    }
  }

  onLoaded = () => {
    this.setState({
      ...this.state,
      loading: false
    });
  }

  selectDefinitionVersion = ({target}) => {
    const {current: {id: previousId}} = this.props;
    const selected = this.props.versions[target.selectedIndex];

    setVersionForProcess(previousId, selected);
    this.setState({
      loading: true
    });
  }
}

export const PreviewCard = createViewUtilsComponentFromReact('div', PreviewCardReact);
