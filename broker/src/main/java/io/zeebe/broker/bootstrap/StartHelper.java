package io.zeebe.broker.bootstrap;

import io.zeebe.broker.Loggers;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public class StartHelper {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<StartStep> startSteps;
  private final CloseHelper closeHelper;
  private final String name;

  public StartHelper(String name) {
    this.name = name;
    this.startSteps = new ArrayList<>();
    this.closeHelper = new CloseHelper(name);
  }

  public void addStep(String name, Runner runnable) {
    startSteps.add(new StartStep(name, () -> {
      runnable.run();
      return () -> { };
    }));
  }

  public void addStep(String name, StartFunction startFunction) {
    startSteps.add(new StartStep(name, startFunction));
  }

  public CloseHelper start() throws Exception {

    final long durationTime = takeDuration(() ->
    {
      int index = 1;
      for (StartStep step : startSteps) {
        LOG.info("Bootstrap {} [{}/{}]: {}", name, index, startSteps.size(), step.getName());
        try {
          final long durationStepStarting = takeDuration(() -> {
            final AutoCloseable closer = step.getStartFunction().start();
            closeHelper.addCloser(step.getName(), closer);
          });
          LOG.debug(
            "Bootstrap {} [{}/{}]: {} started in {} ms",
            name,
            index,
            startSteps.size(),
            step.getName(),
            durationStepStarting);
        }
        catch (Exception startException)
        {
          LOG.info(
            "Bootstrap {} [{}/{}]: {} failed with unexpected exception.",
            name,
            index,
            startSteps.size(),
            step.getName(),
            startException);
          // we need to clean up the already started resources
          closeHelper.closeReverse();
          throw startException;
        }
        index++;
      }
    });
    LOG.info(
        "Bootstrap {} succeeded. Started {} steps in {} ms.",
      name,
        startSteps.size(),
        durationTime);
    return closeHelper;
  }

  public static long takeDuration(Runner runner) throws Exception
  {
    final long startTime = System.currentTimeMillis();
    runner.run();
    final long endTime = System.currentTimeMillis();
    return endTime - startTime;
  }
}
