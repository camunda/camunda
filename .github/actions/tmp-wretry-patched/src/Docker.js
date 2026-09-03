const core = require( '@actions/core' );
if( typeof wTools === 'undefined' )
require( '../node_modules/Joined.s' );
const _ = wTools;
const ChildProcess = require( 'child_process' );
let common = require( './Common.js' );
if( _.map.keys( common ).length === 0 )
{
  const path = require.resolve( './Common.js' );
  common = require.cache[ path ].exports;
}

//

/*
   To run commands synchronously and with no `deasync` this wrapper is used because
   _.process uses Consequence in each `start*` routine and for synchronous execution it requires `deasync`.
   `deasync` is the binary module. To prevent failures during action build and decrease time of setup the action,
   we exclude `deasync` and compile action code.
*/

function execSyncNonThrowing( command )
{
  try
  {
    return ChildProcess.execSync( command, { stdio : 'pipe' } );
  }
  catch( err )
  {
    _.error.attend( err );
    return err;
  }
}

//

function exists()
{
  return !_.error.is( execSyncNonThrowing( 'docker -v' ) );
}

//

function imageBuild( actionPath, image )
{
  const docker = this;

  _.sure
  (
    docker.exists(),
    'Current OS has no Docker utility.\n'
    + 'Please, visit '
    + 'https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#preinstalled-software\n'
    + 'and select valid workflow runner.'
  );

  const actionName = _.path.name( actionPath );
  const imageName = `${ actionName }_repo:${ actionName }_tag`.toLowerCase();

  if( _.uri.isGlobal( image ) )
  {
    const parsed = _.uri.parse( image );
    const publicDockerImage = parsed.longPath;
    const pullCommand = `docker image pull ${ publicDockerImage }`;
    const pull = execSyncNonThrowing( pullCommand );
    if( _.error.is( pull ) )
    throw _.error.brief( pull );

    const tagCommand = `docker image tag ${ publicDockerImage } ${ imageName }`;
    const tagging = execSyncNonThrowing( tagCommand );
    if( _.error.is( tagging ) )
    throw _.error.brief( tagging );

    core.info( `Docker image : ${ publicDockerImage }.` );
    core.info( pullCommand );
    core.info( pull.toString() );
    core.info( tagCommand );
  }
  else if( _.str.ends( image, 'Dockerfile' ) && _.path.isRelative( image ) )
  {
    const dockerfilePath = _.path.nativize( _.path.join( actionPath, image ) );
    const command = `docker build -t ${ imageName } -f ${ dockerfilePath } ${ _.path.nativize( actionPath ) }`;
    const build = execSyncNonThrowing( command );
    if( _.error.is( build ) )
    throw _.error.brief( build );

    core.info( `Dockerfile for action : ${ dockerfilePath }.` );
    core.info( command );
    core.info( build.toString() );
  }
  else
  _.sure
  (
    false,
    `The action does not support requested Docker image type "${ image }".`
    + '\nPlease, open an issue with the request for the feature.'
  );

  return imageName;
}

//

function commandArgsFrom( args, options )
{
  const commandArgs = [];
  if( args === undefined )
  return commandArgs;

  for( let i = 0 ; i < args.length ; i++ )
  {
    let value = args[ i ];
    if( _.str.is( value ) )
    if( value.startsWith( '${{' ) && value.endsWith( '}}' ) )
    {
      const getter = ( name ) =>
      {
        if( name === 'inputs' )
        return options;
        let context = common.contextGet( name );
        return context;
      };
      value = common.evaluateExpression( value, getter );
    }
    if( value === '' )
    {
      core.info
      (
        `Arg "${ args[ i ] }" in position ${ i } is not defined. It can corrupt execution.`
        + `\nPlease, read doc of action and setup it properly`
      );
      value = '""';
    }
    commandArgs.push( String( value ) );
  }

  return commandArgs;
}


//

function runCommandForm( imageName, inputs, args, entrypoint )
{
  const [ repo, tag ] = imageName.split( ':' );
  _.sure( _.str.defined( repo ) && _.str.defined( tag ), 'Expects image name in format "[repo]:[tag]".' );
  const commandEntrypoint = entrypoint === undefined ? '' : `--entrypoint ${ entrypoint } `;
  const command = [ `docker run --name ${ tag } --label ${ repo } ${ commandEntrypoint }--workdir /github/workspace --rm` ];
  const env_keys = _.map.keys( JSON.parse( core.getInput( 'env_context' ) ) );
  const inputs_keys = _.map.keys( inputs );
  const postfix_command_envs =
  [
    'HOME',
    'GITHUB_JOB',
    'GITHUB_REF',
    'GITHUB_SHA',
    'GITHUB_REPOSITORY',
    'GITHUB_REPOSITORY_OWNER',
    'GITHUB_REPOSITORY_OWNER_ID',
    'GITHUB_RUN_ID',
    'GITHUB_RUN_NUMBER',
    'GITHUB_RETENTION_DAYS',
    'GITHUB_RUN_ATTEMPT',
    'GITHUB_REPOSITORY_ID',
    'GITHUB_ACTOR_ID',
    'GITHUB_ACTOR',
    'GITHUB_TRIGGERING_ACTOR',
    'GITHUB_WORKFLOW',
    'GITHUB_HEAD_REF',
    'GITHUB_BASE_REF',
    'GITHUB_EVENT_NAME',
    'GITHUB_SERVER_URL',
    'GITHUB_API_URL',
    'GITHUB_GRAPHQL_URL',
    'GITHUB_REF_NAME',
    'GITHUB_REF_PROTECTED',
    'GITHUB_REF_TYPE',
    'GITHUB_WORKFLOW_REF',
    'GITHUB_WORKFLOW_SHA',
    'GITHUB_WORKSPACE',
    'GITHUB_ACTION',
    'GITHUB_EVENT_PATH',
    'GITHUB_ACTION_REPOSITORY',
    'GITHUB_ACTION_REF',
    'GITHUB_PATH',
    'GITHUB_ENV',
    'GITHUB_STEP_SUMMARY',
    'GITHUB_STATE',
    'GITHUB_OUTPUT',
    'RUNNER_OS',
    'RUNNER_ARCH',
    'RUNNER_NAME',
    'RUNNER_ENVIRONMENT',
    'RUNNER_TOOL_CACHE',
    'RUNNER_TEMP',
    'RUNNER_WORKSPACE',
    'ACTIONS_RUNTIME_URL',
    'ACTIONS_RUNTIME_TOKEN',
    'ACTIONS_CACHE_URL',
    'GITHUB_ACTIONS=true',
    'CI=true',
  ];
  const githubOutputDir = _.path.dir( process.env.GITHUB_OUTPUT );
  const postfix_command_paths =
  [
    '"/var/run/docker.sock":"/var/run/docker.sock"',
    '"/home/runner/work/_temp/_github_home":"/github/home"',
    '"/home/runner/work/_temp/_github_workflow":"/github/workflow"',
    '"/home/runner/work/_temp/_runner_file_commands":"/github/file_commands"',
    `"${ process.env.GITHUB_WORKSPACE }":"/github/workspace"`,
    `"${ githubOutputDir }":"${ githubOutputDir }"`,
  ];

  /* */

  if( env_keys.length > 0 )
  command.push( '-e', env_keys.join( ' -e ' ) );
  if( inputs_keys.length > 0 )
  command.push( '-e', inputs_keys.join( ' -e ' ) );
  command.push( '-e', postfix_command_envs.join( ' -e ' ) );
  command.push( '-v', postfix_command_paths.join( ' -v ' ) );
  command.push( imageName );
  command.push( args.join( ' ' ) );

  const strCommand = command.join( ' ' );
  core.debug( strCommand );
  return strCommand;
}

// --
// export
// --

const Self =
{
  exists,
  imageBuild,
  commandArgsFrom,
  runCommandForm,
};

module.exports = Self;
