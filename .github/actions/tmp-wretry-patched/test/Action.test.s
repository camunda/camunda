( function _Action_test_s_()
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
}

//

function onRoutineEnd()
{
  onSuiteBegin.call( this );
}

// --
// test
// --

function retryWithoutAction( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without action name';
    core.exportVariable( `INPUT_WITH`, 'value : 4' );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shellNonThrowing( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

//

function retryWithUnsupportedAction( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const testAction = 'dmvict/test.action@composite';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'with composite action';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Runner "composite" does not implemented.' ), 1 );
    test.identical( _.strCount( op.output, 'Please, search/open a related issue.' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shellNonThrowing( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

//

function retryWithActionAndCommand( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }`;

  const testAction = 'dmvict/test.action@v0.0.2';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'without action name';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_COMMAND`, 'echo str' );
    core.exportVariable( `INPUT_WITH`, 'value : 4' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, specify Github action name' ), 0 );
    test.identical( _.strCount( op.output, '::error::Expects Github action name or command, but not both.' ), 1 );
    return null;
  });

  a.ready.finally( () =>
  {
    delete process.env.INPUT_COMMAND;
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shellNonThrowing( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

//

function retryLocalAction( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const repoWithWorkflowDir = a.abs( 'test.action' );
  const localActionRepo = 'https://github.com/dmvict/test.action.git';
  const testAction = './.github/actions';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `GITHUB_WORKSPACE`, repoWithWorkflowDir );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
    return null;
  });

  a.ready.finally( () =>
  {
    delete process.env.GITHUB_WORKSPACE;
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
      a.fileProvider.dirMake( repoWithWorkflowDir );
      return null;
    });
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `git clone ${ localActionRepo } ${ a.path.nativize( repoWithWorkflowDir ) }` );
    a.shell({ currentPath : repoWithWorkflowDir, execPath : 'git checkout local_action' });
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

//

function retryFetchActionWithoutTagOrHash( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'actions/hello-world-javascript-action';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'who-to-greet : test' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 1 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 0 );
    test.identical( _.strCount( op.output, /::error::.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Hello, test!' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

//

function retryFetchActionWithTag( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@v0.0.2';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

//

function retryFetchActionWithHash( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'dmvict/test.action';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'full hash, enought attempts';
    core.exportVariable( `INPUT_ACTION`, `${ testAction }@3aa0050099ba8aff344137061782e0c18448cd7d` );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'partial hash, enought attempts';
    core.exportVariable( `INPUT_ACTION`, `${ testAction }@3aa0050` );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

retryFetchActionWithHash.timeOut = 120000;

//

function retryFetchActionWithSubdirectory( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'dmvict/test.action';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'subdirectory, action with tag';
    core.exportVariable( `INPUT_ACTION`, `${ testAction }/subaction@v0.0.11` );
    core.exportVariable( `INPUT_WITH`, 'multiline: |\n  one\n  two,\nstring : "|"'  );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 3 attempts/ ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'subdirectory, action with tag';
    core.exportVariable( `INPUT_ACTION`, `${ testAction }/subaction@366f895` );
    core.exportVariable( `INPUT_WITH`, 'multiline: |\n  one\n  two,\nstring : "|"'  );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

retryFetchActionWithSubdirectory.timeOut = 120000;

//

function retryWithMultilineOptionInOptionWith( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'dmvict/test.action@multiline_option';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'all inputs are valid - multiline string has two or more lines, string is |';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'multiline: |\n  one\n  two,\nstring : "|"'  );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 0 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'multiline string has single line';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'multiline: |\n  one,\nstring : "|"'  );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Expected multiline input with several lines.' ), 4 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts :' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'the value of string is replaced by second line';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'multiline: |\n  one\n  two,\nstring : |\n  str'  );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Expected string with value `|`.' ), 4 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts :' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

retryWithMultilineOptionInOptionWith.timeOut = 120000;

//

function retryWithOptionAttemptLimit( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  const testAction = 'dmvict/test.action@v0.0.2';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'default number of attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '2' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 2 );
    test.identical( _.strCount( op.output, /::error::.*Attempts exhausted, made 2 attempts/ ), 1 );
    test.identical( _.strCount( op.output, 'Success' ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'not enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '3' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::.*Attempts exhausted, made 3 attempts/ ), 1 );
    test.identical( _.strCount( op.output, 'Success' ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

retryWithOptionAttemptLimit.timeOut = 120000;

//

function retryWithOptionAttemptDelay( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'dmvict/test.action@v0.0.2';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, default value of attempt_delay';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  var start;
  a.ready.then( () =>
  {
    start = _.time.now();
    return null;
  });
  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    var spent = _.time.now() - start;
    test.le( spent, 6000 );
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, not default value of attempt_delay';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_ATTEMPT_DELAY`, '4000' );
    start = _.time.now();
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    var spent = _.time.now() - start;
    test.ge( spent, 12000 );
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () => { a.fileProvider.filesDelete( a.abs( '.' ) ); return null; } );
    a.ready.then( () => { a.fileProvider.dirMake( actionPath ); return null; } );
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

//

function retryWithOptionRetryCondition( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'dmvict/test.action@v0.0.2';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, retry_condition returns true';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_RETRY_CONDITION`, '2 < 4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, /::error::.*Process returned exit code/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, retry_condition returns false';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_RETRY_CONDITION`, '2 == 4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 1 );
    test.identical( _.strCount( op.output, /::error::.*Attempts exhausted, made 3 attempts/ ), 0 );
    test.identical( _.strCount( op.output, /::error::.*Process returned exit code/ ), 1 );
    test.identical( _.strCount( op.output, 'Success' ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, retry_condition resolve from context and return false';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_GITHUB_CONTEXT`, '{ "ref_name": "master" }' );
    core.exportVariable( `INPUT_RETRY_CONDITION`, 'github.ref_name == \'main\'' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 1 );
    test.identical( _.strCount( op.output, /::error::.*Attempts exhausted, made 3 attempts/ ), 0 );
    test.identical( _.strCount( op.output, /::error::.*Process returned exit code/ ), 1 );
    test.identical( _.strCount( op.output, 'Success' ), 0 );
    return null;
  });

  /* */

  a.ready.finally( () =>
  {
    core.exportVariable( `INPUT_RETRY_CONDITION`, true );
    return null;
  });

  /* - */

  return a.ready;

  /* */

  function actionSetup()
  {
    a.ready.then( () => { a.fileProvider.filesDelete( a.abs( '.' ) ); return null; } );
    a.ready.then( () => { a.fileProvider.dirMake( actionPath ); return null; } );
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

//

function retryWithOptionPreRetryCommand( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'dmvict/test.action@v0.0.2';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const isTestContainer = _.process.insideTestContainer();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, executing command between retries';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_PRE_RETRY_COMMAND`, 'echo "Executing pre_retry_command"' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, /::error::.*Process returned exit code/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
    test.identical( _.strCount( op.output, 'Executing pre_retry_command' ), 3 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'command between retries trows error, interrupting execution';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_PRE_RETRY_COMMAND`, 'exit 1' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    if( !isTestContainer )
    test.ge( _.strCount( op.output, '::set-env' ), 2 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 2 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, /::error::.*Process returned exit code/ ), 1 );
    test.identical( _.strCount( op.output, 'Success' ), 0 );
    return null;
  });

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
    a.ready.then( () => { a.fileProvider.filesDelete( a.abs( '.' ) ); return null; } );
    a.ready.then( () => { a.fileProvider.dirMake( actionPath ); return null; } );
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

//

function retryWithExternalActionOnLocal( test )
{
  const context = this;
  const a = test.assetFor( false );

  if( _.process.insideTestContainer() )
  return test.true( true  );

  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'actions/setup-node@v2.3.0';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, default value of attempt_delay';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'node-version : 13.x' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    core.exportVariable( `INPUT_GITHUB_CONTEXT`, '{ "token": "github_token" }' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::debug::isExplicit:' ), 4 );
    test.identical( _.strCount( op.output, '::debug::explicit? false' ), 4 );
    test.identical( _.strCount( op.output, '::error::Expected RUNNER_TOOL_CACHE to be defined' ), 4 );
    test.identical( _.strCount( op.output, /::error::.*Attempts exhausted, made 4 attempts/ ), 1 );
    test.identical( _.strCount( op.output, 'Attempting to download 13.x' ), 0 );
    test.identical( _.strCount( op.output, 'Not found in manifest.  Falling back to download directly from Node' ), 0 );
    test.identical( _.strCount( op.output, /Acquiring 13.\d+\.\d+/ ), 0 );
    test.identical( _.strCount( op.output, 'Extracting ...' ), 0 );
    test.identical( _.strCount( op.output, 'Adding to the cache' ), 0 );
    test.identical( _.strCount( op.output, 'Done' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

retryWithExternalActionOnLocal.timeOut = 120000;

//

function retryWithExternalActionOnRemote( test )
{
  const context = this;
  const a = test.assetFor( false );
  const token = process.env.GITHUB_TOKEN;

  if( !token || !_.process.insideTestContainer() )
  return test.true( true  );

  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const testAction = 'actions/setup-node@v2.3.0';
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  core.exportVariable( `INPUT_GITHUB_CONTEXT`, `{"token":"${ token}"}` );

  actionSetup();

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, default value of attempt_delay';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'node-version : 13.x' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath, outputPiping : 0 });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.ge( _.strCount( op.output, '::debug::isExplicit:' ), 0 );
    test.ge( _.strCount( op.output, '::debug::explicit? false' ), 0 );
    test.identical( _.strCount( op.output, '::error::Expected RUNNER_TOOL_CACHE to be defined' ), 0 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Attempting to download 13.x' ), 1 );
    test.identical( _.strCount( op.output, /Acquiring 13.\d+\.\d+/ ), 1 );
    test.identical( _.strCount( op.output, 'Extracting ...' ), 1 );
    test.identical( _.strCount( op.output, 'Adding to the cache' ), 1 );
    test.identical( _.strCount( op.output, 'Done' ), 1 );
    return null;
  });

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts, default value of attempt_delay';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'node-version : 25.x' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath, outputPiping : 0 });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.ge( _.strCount( op.output, '::debug::isExplicit:' ), 0 );
    test.ge( _.strCount( op.output, '::debug::explicit? false' ), 0 );
    test.identical( _.strCount( op.output, '::error::Expected RUNNER_TOOL_CACHE to be defined' ), 0 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, /Acquiring 13.\d+\.\d+/ ), 0 );
    test.identical( _.strCount( op.output, 'Extracting ...' ), 0 );
    test.identical( _.strCount( op.output, 'Adding to the cache' ), 0 );
    test.identical( _.strCount( op.output, 'Done' ), 0 );
    test.identical( _.strCount( op.output, 'Attempting to download 25.x' ), 4 );
    test.identical( _.strCount( op.output, 'error::Unable to find Node version \'25.x\'' ), 4 );
    test.identical( _.strCount( op.output, 'Attempts exhausted, made 4 attempts' ), 1 );
    test.identical( _.strCount( op.output, /Attempt #\d started at/ ), 4 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    return a.ready;
  }
}

retryWithExternalActionOnRemote.timeOut = 240000;

//

function retryActionWithPreScript( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@pre';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, 'Unexpected {-pre_value-}' ), 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    a.ready.then( ( op ) =>
    {
      if( !_.process.insideTestContainer() )
      if( _.str.has( op.output, '::set-env name=INPUT_PRE_VALUE::5' ) );
      core.exportVariable( `INPUT_PRE_VALUE`, '5' );
      return null;
    });
    return a.ready;
  }
}

//

function retryActionWithPostScript( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@post';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, 'Unexpected {-pre_value-}' ), 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );


    if( !_.process.insideTestContainer() )
    if( _.str.has( op.output, '::set-env name=INPUT_MAIN_VALUE::6' ) );
    core.exportVariable( `INPUT_MAIN_VALUE`, '6' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath : `node ${ a.path.nativize( a.abs( actionPath, 'src/Post.js' ) ) }` });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, 'Main script set no valid value {-main_value-}.' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    a.ready.then( ( op ) =>
    {
      if( !_.process.insideTestContainer() )
      if( _.str.has( op.output, '::set-env name=INPUT_PRE_VALUE::5' ) );
      core.exportVariable( `INPUT_PRE_VALUE`, '5' );
      return null;
    });
    return a.ready;
  }
}

retryActionWithPostScript.timeOut = 120000;

//

function retryActionWithPreAndPostScript( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@pre_and_post';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'enought attempts';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value : 0' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, 'Unexpected {-pre_value-}' ), 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 3 );
    test.identical( _.strCount( op.output, '::error::Wrong attempt' ), 3 );
    test.identical( _.strCount( op.output, /::error::undefined.*Attempts exhausted, made 4 attempts/ ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );


    if( !_.process.insideTestContainer() )
    if( _.str.has( op.output, '::set-env name=INPUT_MAIN_VALUE::6' ) );
    core.exportVariable( `INPUT_MAIN_VALUE`, '6' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath : `node ${ a.path.nativize( a.abs( actionPath, 'src/Post.js' ) ) }` });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, 'Main script set no valid value {-main_value-}.' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    a.shell( `node ${ a.path.nativize( a.abs( actionPath, 'src/Pre.js' ) ) }` );
    a.ready.then( ( op ) =>
    {
      if( !_.process.insideTestContainer() )
      if( _.str.has( op.output, '::set-env name=INPUT_PRE_VALUE::5' ) );
      core.exportVariable( `INPUT_PRE_VALUE`, '5' );
      return null;
    });
    return a.ready;
  }
}

retryActionWithPreAndPostScript.timeOut = 120000;

//

function retryActionWithDefaultInputs( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@default_inputs';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'missed required argument, default bool value exists';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'bool: true' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Please, provide value for option "value"' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'missed bool argument, default bool value exists';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value: some' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    var msg = '::error::Input does not meet YAML 1.2 "Core Schema" specification: '
    + 'bool%0ASupport boolean input list: `true | True | TRUE | false | False | FALSE`';
    test.identical( _.strCount( op.output, msg ), 4 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'rewrite default bool value';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value: some\nbool: false\nbool_default: false' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.notIdentical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::Expected default bool value {-bool_default-}' ), 4 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'with default bool value';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value: some\nbool: false' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'write default bool value';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'value: some\nbool: false\nbool_default: true' );
    core.exportVariable( `INPUT_ATTEMPT_LIMIT`, '4' );
    return null;
  });

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryActionWithDefaultInputs.timeOut = 120000;

//

function retryActionWithDefaultInputsAsExpressions( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@defaults_from_expressions';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'missed required argument, default bool value exists';
    core.exportVariable( `INPUT_ACTION`, testAction );
    process.env.TEST = 'test';
    const github_context =
`{
  "retention_days": "90",
  "event": {
    "ref": "refs/heads/exp2"
  }
}`;
    core.exportVariable( 'INPUT_ENV_CONTEXT', '{}' );
    core.exportVariable( 'INPUT_GITHUB_CONTEXT', github_context );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    if( !_.process.insideTestContainer() )
    test.ge( _.strCount( op.output, '::set-env' ), 5 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryActionWithDefaultInputsAsExpressions.timeOut = 120000;

//

function retryActionWithDefaultInputsFromJobContext( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@defaults_from_job_context';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'missed required argument, default bool value exists';
    core.exportVariable( `INPUT_ACTION`, testAction );
    const job_context =
`{
  "status": "success",
  "container": {
    "network": "github_network"
  },
  "services": {
    "postgres": {
      "network": "github_network"
    }
  }
}`
    core.exportVariable( `INPUT_JOB_CONTEXT`, job_context );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryActionWithDefaultInputsFromJobContext.timeOut = 120000;

//

function retryActionWithDefaultInputsFromMatrixContext( test )
{
  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action@defaults_from_matrix_context';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'missed required argument, default bool value exists';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_MATRIX_CONTEXT`, '{"os":"ubuntu-latest"}' );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryActionWithDefaultInputsFromMatrixContext.timeOut = 120000;

//

function retryPrivateActionWithoutInputsMap( test )
{
  const context = this;
  const a = test.assetFor( false );

  if( !process.env.PRIVATE_TOKEN )
  return test.true( true );

  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;

  const testAction = 'dmvict/test.action.private@master';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'missed required argument, default bool value exists';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_GITHUB_TOKEN`, process.env.PRIVATE_TOKEN );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    test.identical( _.strCount( op.output, 'Success' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryPrivateActionWithoutInputsMap.timeOut = 120000;

//

function retryDockerTrivialAction( test )
{
  const ubuntuIs = process.env.ImageOS && _.str.begins( process.env.ImageOS, 'ubuntu' );

  if( !_.process.insideTestContainer() || !ubuntuIs )
  return test.true( true );

  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const testAction = 'actions/hello-world-docker-action@main';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'run docker action';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_ENV_CONTEXT`, '{}' );
    core.exportVariable( `INPUT_INPUTS_CONTEXT`, '{}' );
    core.exportVariable( `GITHUB_WORKSPACE`, actionPath );
    core.exportVariable( `GITHUB_OUTPUT`, `${ actionPath }/github_output` );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    test.identical( _.strCount( op.output, /Dockerfile for action : .*\/Dockerfile/ ), 1 );
    test.identical( _.strCount( op.output, 'docker build -t hello-world-docker-action_repo:hello-world-docker-action_tag' ), 1 );
    test.le( _.strCount( op.output, 'Successfully built' ), 1 );
    test.le( _.strCount( op.output, 'Successfully tagged hello-world-docker-action_repo:hello-world-docker-action_tag' ), 1 );
    test.identical( _.strCount( op.output, 'Hello' ), 1 );
    test.identical( _.strCount( op.output, 'time=' ), 0 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryDockerTrivialAction.timeOut = 120000;

//

function retryDockerActionWithInputs( test )
{
  const ubuntuIs = process.env.ImageOS && _.str.begins( process.env.ImageOS, 'ubuntu' );

  if( !_.process.insideTestContainer() || !ubuntuIs )
  return test.true( true );

  const context = this;
  const a = test.assetFor( false );
  const actionPath = a.abs( '_action/actions/wretry.action/v1' );
  const execPath = `node ${ a.path.nativize( a.abs( actionPath, 'src/Main.js' ) ) }`;
  const testAction = 'dmvict/test.action@docker';

  /* - */

  a.ready.then( () =>
  {
    test.case = 'use default';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'string: foo' );
    core.exportVariable( `INPUT_ENV_CONTEXT`, '{}' );
    core.exportVariable( `INPUT_INPUTS_CONTEXT`, '{}' );
    core.exportVariable( `GITHUB_WORKSPACE`, actionPath );
    core.exportVariable( `GITHUB_OUTPUT`, `${ actionPath }/github_output` );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    test.identical( _.strCount( op.output, /Dockerfile for action : .*\/Dockerfile/ ), 1 );
    test.identical( _.strCount( op.output, 'docker build -t test_repo:test_tag' ), 1 );
    test.le( _.strCount( op.output, 'Successfully built' ), 1 );
    test.le( _.strCount( op.output, 'Successfully tagged test_repo:test_tag' ), 1 );
    test.identical( _.strCount( op.output, 'string=foo' ), 1 );
    test.identical( _.strCount( op.output, 'bool-like=true' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'setup not default value, false';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'string: foo\nbool-like: false' );
    core.exportVariable( `INPUT_ENV_CONTEXT`, '{}' );
    core.exportVariable( `INPUT_INPUTS_CONTEXT`, '{}' );
    core.exportVariable( `GITHUB_WORKSPACE`, actionPath );
    core.exportVariable( `GITHUB_OUTPUT`, `${ actionPath }/github_output` );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    test.identical( _.strCount( op.output, /Dockerfile for action : .*\/Dockerfile/ ), 1 );
    test.identical( _.strCount( op.output, 'docker build -t test_repo:test_tag' ), 1 );
    test.le( _.strCount( op.output, 'Successfully built' ), 1 );
    test.le( _.strCount( op.output, 'Successfully tagged test_repo:test_tag' ), 1 );
    test.identical( _.strCount( op.output, 'string=foo' ), 1 );
    test.identical( _.strCount( op.output, 'bool-like=false' ), 1 );
    return null;
  });

  /* */

  a.ready.then( () =>
  {
    test.case = 'setup not default value, not false';
    core.exportVariable( `INPUT_ACTION`, testAction );
    core.exportVariable( `INPUT_WITH`, 'string: foo\nbool-like: 0' );
    core.exportVariable( `INPUT_ENV_CONTEXT`, '{}' );
    core.exportVariable( `INPUT_INPUTS_CONTEXT`, '{}' );
    core.exportVariable( `GITHUB_WORKSPACE`, actionPath );
    core.exportVariable( `GITHUB_OUTPUT`, `${ actionPath }/github_output` );
    return null;
  });

  actionSetup();

  a.shellNonThrowing({ currentPath : actionPath, execPath });
  a.ready.then( ( op ) =>
  {
    test.identical( op.exitCode, 0 );
    test.identical( _.strCount( op.output, '::error::' ), 0 );
    test.identical( _.strCount( op.output, /Dockerfile for action : .*\/Dockerfile/ ), 1 );
    test.identical( _.strCount( op.output, 'docker build -t test_repo:test_tag' ), 1 );
    test.le( _.strCount( op.output, 'Successfully built' ), 1 );
    test.le( _.strCount( op.output, 'Successfully tagged test_repo:test_tag' ), 1 );
    test.identical( _.strCount( op.output, 'string=foo' ), 1 );
    test.identical( _.strCount( op.output, 'bool-like=0' ), 1 );
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
    a.shell( `git clone ${ a.path.nativize( context.actionDirPath ) } ${ a.path.nativize( actionPath ) }` );
    return a.ready;
  }
}

retryDockerActionWithInputs.timeOut = 120000;

// --
// declare
// --

const Proto =
{
  name : 'Action',
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
    retryWithoutAction,
    retryWithUnsupportedAction,
    retryWithActionAndCommand,

    retryLocalAction,
    retryFetchActionWithoutTagOrHash,
    retryFetchActionWithTag,
    retryFetchActionWithHash,
    retryFetchActionWithSubdirectory,

    retryWithMultilineOptionInOptionWith,

    retryWithOptionAttemptLimit,
    retryWithOptionAttemptDelay,
    retryWithOptionRetryCondition,
    retryWithOptionPreRetryCommand,

    retryWithExternalActionOnLocal,
    retryWithExternalActionOnRemote,

    retryActionWithPreScript,
    retryActionWithPostScript,
    retryActionWithPreAndPostScript,

    retryActionWithDefaultInputs,
    retryActionWithDefaultInputsAsExpressions,
    retryActionWithDefaultInputsFromJobContext,
    retryActionWithDefaultInputsFromMatrixContext,

    retryPrivateActionWithoutInputsMap,

    //

    retryDockerTrivialAction,
    retryDockerActionWithInputs,
  },
};

//

const Self = wTestSuite( Proto );
if( typeof module !== 'undefined' && !module.parent )
wTester.test( Self.name );

})();

