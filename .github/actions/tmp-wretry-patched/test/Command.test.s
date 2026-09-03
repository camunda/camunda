( function _Command_test_s_()
{

'use strict';

if( typeof module !== 'undefined' )
{
  const _ = require( 'wTools' );
  _.include( 'wTesting' );
}

const _ = _global_.wTools;
const __ = _globals_.testing.wTools;
const core = require( '@actions/core' );

//

function onSuiteBegin()
{
  let context = this;
  context.actionDirPath = __.path.join( __dirname, '..' );
}

//

function onRoutineBegin()
{
  delete process.env.INPUT_ACTION;
  delete process.env.INPUT_COMMAND;
  delete process.env.INPUT_ATTEMPT_LIMIT;
  delete process.env.INPUT_WITH;
  delete process.env.INPUT_ATTEMPT_DELAY;
  delete process.env.INPUT_RETRY_CONDITION;
  delete process.env.GITHUB_OUTPUT;
}

//

function onRoutineEnd()
{
  onSuiteBegin.call( this );
}

// --
// test
// --

function retryWithoutCommand( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without command and action name';
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryWithWrongCommand( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without action name';
    core.exportVariable( `INPUT_COMMAND`, 'wrong command' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, '::error::Process returned exit code' ), 1 );
    if( process.platform === 'win32' )
    test.identical( _.strCount( op.output, /Launched as \"pwsh .*\"/ ), 1 );
    else
    test.identical( _.strCount( op.output, /Launched as \"bash .*\"/ ), 1 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

retryWithWrongCommand.timeOut = 120000;

//

function retryWithValidCommand( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without action name';
    core.exportVariable( `INPUT_COMMAND`, 'echo str' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    test.identical( _.strCount( op.output, 'str' ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryWithOptionCurrentPath( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const command = process.platform === 'win32' ? 'Get-Location' : 'echo $PWD'

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without current path';
    core.exportVariable( `INPUT_COMMAND`, command );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    if( process.platform !== 'win32' )
    test.identical( _.strCount( op.output, a.path.nativize( actionPath ) ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'without current path';
    core.exportVariable( `INPUT_COMMAND`, command );
    core.exportVariable( `INPUT_CURRENT_PATH`, '../../..' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    test.identical( _.strCount( op.output, a.path.nativize( a.abs( actionPath, '../../..' ) ) ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryWithOptionRetryConditionAndCheckOfStepOutput( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'multiline command';
    if( process.platform === 'win32' )
    core.exportVariable( `INPUT_COMMAND`, '|\necho $Env:GITHUB_OUTPUT\necho "foo=bar" >> $Env:GITHUB_OUTPUT\nexit 1' );
    else
    core.exportVariable( `INPUT_COMMAND`, '|\necho $GITHUB_OUTPUT\necho "foo=bar" >> $GITHUB_OUTPUT\nexit 1' );
    core.exportVariable( `INPUT_STEPS_CONTEXT`, '{}' );
    core.exportVariable( `INPUT_RETRY_CONDITION`, `steps._this.outputs.foo == 'bar'` );
    core.exportVariable( `GITHUB_OUTPUT`, `${ a.path.nativize( a.abs( actionPath, 'github_output' ) ) }` );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    console.log( op.output );
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'multiline command';
    if( process.platform === 'win32' )
    core.exportVariable( `INPUT_COMMAND`, '|\necho $Env:GITHUB_OUTPUT\necho "foo=bar" >> $Env:GITHUB_OUTPUT\nexit 1' );
    else
    core.exportVariable( `INPUT_COMMAND`, '|\necho $GITHUB_OUTPUT\necho "foo=bar" >> $GITHUB_OUTPUT\nexit 1' );
    core.exportVariable( `INPUT_STEPS_CONTEXT`, '{}' );
    core.exportVariable( `INPUT_RETRY_CONDITION`, `steps._this.outputs.foo == 'foo'` );
    core.exportVariable( `GITHUB_OUTPUT`, `${ a.path.nativize( a.abs( actionPath, 'github_output' ) ) }` );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    return null;
  });

  a.ready.finally( () =>
  {
    core.exportVariable( `INPUT_RETRY_CONDITION`, true );
    return null;
  });

  a.ready.finally( () =>
  {
    delete process.env.GITHUB_OUTPUT;
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryWithOptionPreRetryCommand( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const command = 'exit 1';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without current path';
    core.exportVariable( `INPUT_COMMAND`, command );
    core.exportVariable( `INPUT_PRE_RETRY_COMMAND`, 'echo "Executing pre_retry_command"' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    console.log( op.output );
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 1 );
    test.identical( _.strCount( op.output, 'Executing pre_retry_command' ), 3 );
    return null;
  });

  /* */

  a.ready.finally( () =>
  {
    delete process.env.INPUT_PRE_RETRY_COMMAND;
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryWithMultilineCommand( test )
{
  let context = this;

  if( process.platform === 'win32' )
  return test.true( true );

  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'multiline command';
    if( process.platform === 'win32' )
    core.exportVariable( `INPUT_COMMAND`, '|\n  echo `\n  str' );
    else
    core.exportVariable( `INPUT_COMMAND`, '|\n  echo \\\n  str' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    test.identical( _.strCount( op.output, 'str' ), 1 );
    return null;
  });

  //

  a.ready.then( () =>
  {
    test.case = 'several multiline command';
    if( process.platform === 'win32' )
    core.exportVariable( `INPUT_COMMAND`, '|\n  echo `\n    str\n  echo `\n    bar' );
    else
    core.exportVariable( `INPUT_COMMAND`, '|\n  echo \\\n    str\n  echo \\\n    bar' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    test.identical( _.strCount( op.output, 'str' ), 1 );
    test.identical( _.strCount( op.output, 'bar' ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryAndCheckRuntimeEnvironments( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'multiline command';
    if( process.platform === 'win32' )
    core.exportVariable( `INPUT_COMMAND`, '|\n  $FOO="foo" \n  $BAR="bar" \n  echo $FOO$BAR' );
    else
    core.exportVariable( `INPUT_COMMAND`, '|\n  export FOO=foo \n  export BAR=bar \n  echo $FOO$BAR' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    test.identical( _.strCount( op.output, 'foobar' ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}

//

function retryCheckRetryTime( test )
{
  let context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const preExecPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }`;
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'command is succeful and does not overflow time limit';
    core.exportVariable( 'INPUT_COMMAND', '|\n  sleep 1 \n  echo str' );
    core.exportVariable( 'INPUT_TIME_OUT', '5000' );
    core.exportVariable( 'INPUT_ATTEMPT_LIMIT', '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath : preExecPath });
  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    test.identical( _.strCount( op.output, 'str' ), 1 );
    if( process.platform === 'win32' )
    test.identical( _.strCount( op.output, '::error::Process returned exit code 1' ), 0 );
    else
    test.identical( _.strCount( op.output, '::error::Process was killed by exit signal SIGTERM' ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'command is succeful and overflows time limit';
    core.exportVariable( 'INPUT_COMMAND', '|\n  sleep 10 \n  echo str' );
    core.exportVariable( 'INPUT_TIME_OUT', '3000' );
    core.exportVariable( 'INPUT_ATTEMPT_LIMIT', '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath : preExecPath });
  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 1 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    if( process.platform === 'win32' )
    {
      test.le( _.strCount( op.output, 'str' ), 2 );
      test.identical( _.strCount( op.output, '::error::Process returned exit code 1' ), 1 );
    }
    else
    {
      test.identical( _.strCount( op.output, 'str' ), 0 );
      test.identical( _.strCount( op.output, '::error::Process was killed by exit signal SIGTERM' ), 1 );
    }
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'command is failed and does not overflow time limit';
    core.exportVariable( 'INPUT_COMMAND', '|\n  sleep 1 \n  echo str \n  exit 1' );
    core.exportVariable( 'INPUT_TIME_OUT', '12000' );
    core.exportVariable( 'INPUT_ATTEMPT_LIMIT', '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath : preExecPath });
  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 1 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 1 );
    test.identical( _.strCount( op.output, 'str' ), 4 );
    if( process.platform === 'win32' )
    test.identical( _.strCount( op.output, '::error::Process returned exit code 1' ), 1 );
    else
    test.identical( _.strCount( op.output, '::error::Process was killed by exit signal SIGTERM' ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'command is failed and overflows time limit';
    core.exportVariable( 'INPUT_COMMAND', '|\n  sleep 4 \n  echo str \n  exit 1' );
    core.exportVariable( 'INPUT_TIME_OUT', '6000' );
    core.exportVariable( 'INPUT_ATTEMPT_LIMIT', '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath : preExecPath });
  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 1 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 0 );
    if( process.platform === 'win32' )
    {
      test.le( _.strCount( op.output, 'str' ), 3 );
      test.identical( _.strCount( op.output, '::error::Process returned exit code 1' ), 1 );
    }
    else
    {
      test.identical( _.strCount( op.output, 'str' ), 1 );
      test.identical( _.strCount( op.output, '::error::Process was killed by exit signal SIGTERM' ), 1 );
    }
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () =>
    {
      a.fileProvider.filesDelete( a.abs( '.' ) );
      a.fileProvider.dirMake( actionPath );
      return null;
    });
    a.ready.then( () =>
    {
      return __.git.repositoryClone
      ({
        localPath : actionPath,
        remotePath : __.git.path.normalize( context.actionDirPath ),
        attemptLimit : 4,
        attemptDelay : 250,
        attemptDelayMultiplier : 4,
      });
    });
    return a.ready;
  }
}
retryCheckRetryTime.timeOut = 240000;

// --
// declare
// --

const Proto =
{
  name : 'Command',
  silencing : 1,
  routineTimeOut : 60000,

  onSuiteBegin,
  onRoutineBegin,
  onRoutineEnd,

  context :
  {
    actionDirPath : null,
  },

  tests :
  {
    retryWithoutCommand,
    retryWithWrongCommand,
    retryWithValidCommand,

    retryWithOptionCurrentPath,
    retryWithOptionRetryConditionAndCheckOfStepOutput,
    retryWithOptionPreRetryCommand,

    retryWithMultilineCommand,
    retryAndCheckRuntimeEnvironments,
    retryCheckRetryTime,
  },
};

//

const Self = wTestSuite( Proto );
if( typeof module !== 'undefined' && !module.parent )
wTester.test( Self.name );

})();

