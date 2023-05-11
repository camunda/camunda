package io.camunda.zeebe.zbctl;

import io.camunda.zeebe.zbctl.cmd.StatusCommand;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;

@TopCommand
@Command(
    name = "zbctl",
    exitCodeListHeading = "Exit codes:%n",
    exitCodeList = {"0: Successful exit", "1: Application error"},
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    description = {
      "zbctl is a command line interface designed to create and read resources inside zeebe broker.",
      "It is designed for regular maintenance jobs such as:",
      "\t* deploying processes",
      "\t* creating jobs and process instances",
      "\t* activating, completing or failing jobs",
      "\t* update variables and retries",
      "\t* view cluster status"
    },
    usageHelpAutoWidth = true,
    subcommands = {HelpCommand.class, StatusCommand.class})
public final class Application implements Callable<Integer> {

  @Override
  public Integer call() throws Exception {
    return ExitCode.OK;
  }
}
