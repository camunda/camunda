const core = require( '@actions/core' );
const actionName = core.getInput( 'action' );

if( actionName )
{
  const { retry } = require( './Retry.js' );
  retry( 'post' );
}
