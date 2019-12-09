package io.zeebe.broker.bootstrap;

import static io.zeebe.broker.bootstrap.StartHelper.takeDuration;

import io.zeebe.broker.Broker;
import io.zeebe.broker.Loggers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public class CloseHelper {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<CloseStep> closeableSteps;
  private final int nodeId;

  public CloseHelper(int nodeId) {
    this.nodeId = nodeId;
    this.closeableSteps = new ArrayList<>();
  }

  public void addCloser(String name, AutoCloseable closingFunction)
  {
    closeableSteps.add(new CloseStep(name, closingFunction));
  }

  public void closeReverse() {
    Collections.reverse(closeableSteps);

    try {
      final long durationTime = takeDuration(() ->
      {
        int index = 1;

        for (CloseStep closeableStep : closeableSteps) {
          try {
            LOG.info("Closing {} [{}/{}]: {}", nodeId, index, closeableSteps.size(), closeableStep.getName());
            final long durationStepStarting = takeDuration(() -> closeableStep.getClosingFunction().close());
            Broker.LOG.info(
              "Closing {} [{}/{}]: {} closed in {} ms",
              nodeId,
              index,
              closeableSteps.size(),
              closeableStep.getName(),
              durationStepStarting);
          } catch (Exception exceptionOnClose) {
            Broker.LOG.error(
              "Closing {} [{}/{}]: {} failed to close.",
              nodeId,
              index,
              closeableSteps.size(),
              closeableStep.getName(), exceptionOnClose);
            // continue with closing others
          }
          index++;
        }
      });
      Broker.LOG.info(
        "Closing {} succeeded. Closed {} steps in {} ms.",
        nodeId,
        closeableSteps.size(),
        durationTime);
    } catch (Exception willNeverHappen)
    {
      LOG.error("Unexpected exception occured on closing {}", nodeId, willNeverHappen);
    }
  }
}
