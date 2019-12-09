package io.zeebe.broker.bootstrap;

import io.zeebe.broker.Broker;
import java.util.ArrayList;
import java.util.List;

public class StartHelper {
  private final List<StartStep> startSteps;
  private final CloseHelper closeHelper;
  private final int nodeId;

  public StartHelper(int nodeId) {
    this.nodeId = nodeId;
    this.startSteps = new ArrayList<>();
    this.closeHelper = new CloseHelper(nodeId);
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
        Broker.LOG.info("Bootstrap {} [{}/{}]: {}", nodeId, index, startSteps.size(), step.getName());
        try {
          final long durationStepStarting = takeDuration(() -> {
            final AutoCloseable closer = step.getStartFunction().start();
            closeHelper.addCloser(step.getName(), closer);
          });
          Broker.LOG.info(
            "Bootstrap {} [{}/{}]: {} succeeded in {} ms",
            nodeId,
            index,
            startSteps.size(),
            step.getName(),
            durationStepStarting);
        }
        catch (Exception startException)
        {
          Broker.LOG.info(
            "Bootstrap {} [{}/{}]: {} failed with unexpected exception.",
            nodeId,
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
    Broker.LOG.info(
        "Bootstrap {} succeeded. Started {} steps in {} ms.",
        nodeId,
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
