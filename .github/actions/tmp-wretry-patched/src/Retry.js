const core = require( '@actions/core' );
const common = require( './Common.js' );
require( '../node_modules/Joined.s' );
const _ = wTools;

//

function retry( scriptType )
{
  let shouldRetry = core.getInput( 'retry_condition' ) || true;
  let isRetry = false;
  const actionName = core.getInput( 'action' );
  const command = core.getMultilineInput( 'command' );
  const preRetryCommand = core.getMultilineInput( 'pre_retry_command' );

  let currentPath = core.getInput( 'current_path' ) || _.path.current();
  if( !_.path.isAbsolute( currentPath ) )
  currentPath = _.path.join( _.path.current(), currentPath );

  let preRetryExecPath = null;
  if( preRetryCommand.length > 0 )
  preRetryExecPath = execPathFromCommandAndNameForm( preRetryCommand, 'pre_script' );

  let startTime = null;
  const timeLimit = _.number.from( core.getInput( 'time_out' ) ) || null;
  let timeoutGet = () => null;
  if( timeLimit )
  {
    startTime = _.time.now();
    timeoutGet = () =>
    {
      let now = _.time.now();
      let spent = now - startTime;
      if( spent >= timeLimit )
      shouldRetry = false;
      return timeLimit - spent;
    };
  }

  return _.Consequence.Try( () =>
  {
    let routine;
    const con = _.take( null );

    if( !actionName )
    {
      const execPath = execPathFromCommandAndNameForm( command, 'script' );
      routine = () =>
      {
        const o =
        {
          currentPath,
          execPath,
          inputMirroring : 0,
          stdio : 'inherit',
          mode : 'shell',
        };
        o.timeOut = timeoutGet();
        _.process.start( o );
        return o.ready;
      };
    }
    else
    {
      if( command.length )
      throw _.error.brief( 'Expects Github action name or command, but not both.' );

      process.env.RETRY_ACTION = actionName;
      const token = core.getInput( 'github_token' );

      const localIs = actionName.startsWith( './' );

      let remoteActionPath = null;
      if( !localIs )
      remoteActionPath = common.remotePathForm( actionName, token );

      const localActionDir = localIs ?
        _.path.nativize( _.path.join( process.env.GITHUB_WORKSPACE, actionName ) ) :
        _.path.nativize( _.path.join( __dirname, '../../../', remoteActionPath.repo ) );

      if( !localIs )
      con.then( () => common.actionClone( localActionDir, remoteActionPath ) );

      con.then( () =>
      {
        const actionFileDir = localIs ? localActionDir : _.path.nativize( _.path.join( localActionDir, remoteActionPath.localVcsPath ) );
        const config = common.actionConfigRead( actionFileDir );

        if( common.shouldExit( config, scriptType ) )
        return null;

        const currentPath = process.env.GITHUB_WORKSPACE || _.path.current();

        const optionsStrings = core.getInput( 'with' );
        const options = common.actionOptionsParse( optionsStrings );

        let fullOptions = Object.create( null );
        let envOptions = Object.create( null );
        if( config.inputs && _.map.keys( config.inputs ).length > 0 )
        {
          _.map.sureHasOnly( options, config.inputs );
          fullOptions = common.optionsExtendByInputDefaults( options, config.inputs );
          envOptions = common.envOptionsFrom( fullOptions );
          common.envOptionsSetup( envOptions );
        }
        else
        {
          if( config.inputs )
          _.sure( _.map.keys( config.inputs ).length === 0, 'Expects no options' );
        }

        if( _.str.begins( config.runs.using, 'node' ) )
        {
          const node = process.argv[ 0 ];
          const runnerPath = _.path.nativize( _.path.join( __dirname, 'Runner.mjs' ) );
          const scriptPath = _.path.nativize( _.path.join( actionFileDir, config.runs[ scriptType ] ) );
          routine = () =>
          {
            const o =
            {
              currentPath,
              execPath : `${ node } ${ runnerPath } ${ scriptPath }`,
              inputMirroring : 0,
              stdio : 'inherit',
              mode : 'spawn',
              ipc : 1,
            };
            o.timeOut = timeoutGet();
            _.process.start( o );
            o.pnd.on( 'message', ( data ) => _.map.extend( process.env, data ) );
            return o.ready;
          };
        }
        else if( config.runs.using === 'docker' )
        {
          const docker = require( './Docker.js' );
          const imageName = docker.imageBuild( actionFileDir, config.runs.image );
          const args = docker.commandArgsFrom( config.runs.args, fullOptions );
          const entrypoint = scriptType === 'main' ? config.runs.entrypoint : config.runs[ `${ scriptType }-entrypoint` ];
          const execPath = docker.runCommandForm( imageName, envOptions, args, entrypoint );

          routine = () =>
          {
            const o =
            {
              currentPath,
              execPath,
              inputMirroring : 1,
              stdio : 'inherit',
              mode : 'shell',
            };
            _.process.start( o );
            return o.ready;
          };
        }
        else
        {
          throw _.error.brief
          (
            `Runner "${ config.runs.using }" does not implemented.\nPlease, search/open a related issue.`
          );
        }
        return null;
      });
    }

    /* */

    const attemptLimit = _.number.from( core.getInput( 'attempt_limit' ) ) || 2;
    const attemptDelay = _.number.from( core.getInput( 'attempt_delay' ) ) || 0;

    return con.then( () =>
    {
      if( routine )
      {
        const githubOutputCleanRoutine = () =>
        {
          if( process.env.GITHUB_OUTPUT && _.fileProvider.fileExists( process.env.GITHUB_OUTPUT ) )
          _.fileProvider.fileWrite( process.env.GITHUB_OUTPUT, '' );

          if( isRetry && preRetryCommand.length > 0 )
          {
            const o =
            {
              currentPath,
              execPath : preRetryExecPath,
              inputMirroring : 0,
              stdio : 'inherit',
              mode : 'shell',
            };
            o.timeOut = timeoutGet();
            _.process.start( o );

            return o.ready.catch( ( err ) =>
            {
              _.error.attend( err );
              shouldRetry = false;
              return err;
            })
            .then( () =>
            {
              return routine();
            });
          }
          else
          {
            return routine();
          }
        };

        return _.retry
        ({
          routine : githubOutputCleanRoutine,
          attemptLimit,
          attemptDelay,
          onSuccess,
          onError,
        });
      }

      return null;
    });
  })
  .catch( ( error ) =>
  {
    _.error.attend( error );
    core.setFailed( _.error.brief( error.message ) );
    return error;
  });

  /* */

  function onSuccess( arg )
  {
    if( arg.exitCode !== 0 )
    return false;
    return true
  }

  function onError( err )
  {
    _.error.attend( err );

    isRetry = true;

    if( _.bool.is( shouldRetry ) )
    return shouldRetry;
    return !!common.evaluateExpression( shouldRetry );
  }

  function execPathFromCommandAndNameForm( command, scriptName )
  {

    const commands = common.commandsForm( command );
    const commandsScriptPath = _.path.join( __dirname, process.platform === 'win32' ? `${ scriptName }.ps1` : `${ scriptName }.sh` );
    _.fileProvider.fileWrite( commandsScriptPath, commands.join( '\n' ) );

    const execPath = process.platform === 'win32' ?
      `pwsh -command ". '${ _.path.nativize( commandsScriptPath ) }'"` :
      `bash --noprofile --norc -eo pipefail ${ _.path.nativize( commandsScriptPath ) }`;

    return execPath;
  }
}

module.exports = { retry };

