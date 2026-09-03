const core = require( '@actions/core' );

if( process.platform === 'win32' )
{
  const timeLimit = Number( core.getInput( 'time_out' ) ) || null;
  if( timeLimit )
  {
    const childProcess = require( 'child_process' );
    childProcess.execSync( 'git clone https://github.com/Wandalen/wProcessTreeWindows.git ./node_modules/w.process.tree.windows' );
    childProcess.execSync( 'npm i', { cwd: './node_modules/w.process.tree.windows', stdio: 'ignore' } );
  }
}


if( !core.getInput( 'action' ) )
return;

const { retry } = require( './Retry.js' );
retry( 'pre' );
