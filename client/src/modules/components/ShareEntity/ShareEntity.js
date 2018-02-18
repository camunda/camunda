import React from 'react';
import {CopyToClipboard, Switch} from 'components'

import './ShareEntity.css';



export default class ShareEntity extends React.Component {

  constructor(props) {
    super(props);

    this.shareEntity = this.props.shareEntity;
    this.revokeEntitySharing = this.props.revokeEntitySharing;
    this.getSharedEntity = this.props.getSharedEntity;

    this.state = {
      loaded: false, 
      checked: false,
      resourceId: this.props.resourceId,
      id: ''
    }

    this.loadSharedEntity();
  }

  loadSharedEntity = async () => {
    const id = await this.getSharedEntity(this.props.resourceId);
    this.setState(
      {
        id,
        resourceId: this.props.resourceId,
        checked: id
      }
    );
    this.setState({
      loaded: true
    })
  }

  toggleValue = async ({target: {checked}}) => {
    this.setState(prevState => {
        return {
          checked
        };
    });
    if(checked) {
      const id = await this.shareEntity(this.state.resourceId);
      this.setState({id});
    } else {
      await this.revokeEntitySharing(this.state.id);
      this.setState({id: ''});
    }
  }

  buildShareLink = () => {
    if(this.state.id) {
      return `${window.location.origin}/share/${this.props.type}/${this.state.id}`;
    } else {
      return '';
    }
  }
  
  buildShareLinkForEmbedding = () => {
    if(this.state.id) {
      return `<iframe src="${this.buildShareLink()}" frameborder="0" style="width: 1000px; height: 700px; allowtransparency; overflow: scroll"></iframe>`;
    } else {
      return '';
    }
  }

  disabled = () => {
    return !this.state.checked;
  }
  
  render() {
    if(!this.state.loaded) {
      return <div className='ShareEntity__loading-indicator'>loading...</div>;
    }

    return (
      <div className='ShareEntity'>
        <form>
          <div className='ShareEntity__enable'>
            <div className='ShareEntity__enable-text' >Enable sharing </div>
            <div className='ShareEntity__enable-switch'><Switch checked={this.state.checked} onChange={this.toggleValue}/></div>
          </div>
          <div className={('ShareEntity__link-area') + (this.disabled()? '--disabled': '')}>
            <div className='ShareEntity__clipboard'>
              <label className='ShareEntity__label' htmlFor="ShareLink">Link</label>
              <span className='ShareEntity__label-description'>{`Use the following URL to share the ${this.props.type} 
                with people who don't have a Camunda Optimize account:`}</span>
              <CopyToClipboard id={"ShareLink"} disabled={this.disabled()} value={this.buildShareLink()} />
            </div>
            <div className='ShareEntity__clipboard'>
              <label className='ShareEntity__label' htmlFor="ShareEmbed">Embed</label>
              <span className='ShareEntity__label-description'>{`Use the following URL to embed the ${this.props.type} into blogs and web pages:`}</span>
              <CopyToClipboard id={"ShareEmbed"} disabled={this.disabled()} value={this.buildShareLinkForEmbedding()} />
            </div>
          </div>
        </form>
      </div>
    );
  }
}