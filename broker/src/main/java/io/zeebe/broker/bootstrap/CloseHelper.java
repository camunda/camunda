package io.zeebe.broker.bootstrap;

import static io.zeebe.broker.bootstrap.StartHelper.takeDuration;

import io.zeebe.broker.Loggers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public class CloseHelper implements AutoCloseable {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<CloseStep> closeableSteps;
  private final String name;

  public CloseHelper(String name) {
    this.name = name;
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
            LOG.info("Closing {} [{}/{}]: {}", name, index, closeableSteps.size(), closeableStep.getName());
            final long durationStepStarting = takeDuration(() -> closeableStep.getClosingFunction().close());
            LOG.debug(
              "Closing {} [{}/{}]: {} closed in {} ms",
              name,
              index,
              closeableSteps.size(),
              closeableStep.getName(),
              durationStepStarting);
          } catch (Exception exceptionOnClose) {
            LOG.error(
              "Closing {} [{}/{}]: {} failed to close.",
              name,
              index,
              closeableSteps.size(),
              closeableStep.getName(), exceptionOnClose);
            // continue with closing others
          }
          index++;
        }
      });
      LOG.info(
        "Closing {} succeeded. Closed {} steps in {} ms.",
        name,
        closeableSteps.size(),
        durationTime);
    } catch (Exception willNeverHappen)
    {
      LOG.error("Unexpected exception occured on closing {}", name, willNeverHappen);
    }
  }

  @Override
  public void close() {
    closeReverse();
  }
}
