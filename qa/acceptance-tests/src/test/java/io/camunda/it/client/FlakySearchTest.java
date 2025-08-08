package io.camunda.it.client;

import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlakySearchTest {

  @Test
  public void testRandomFailure() {
    // This test will fail randomly about 90% of the time
    Random random = new Random();
    boolean shouldPass = random.nextDouble() > 0.1;

    // Add some delay to make it more realistic
    try {
      Thread.sleep(100 + random.nextInt(200));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertTrue(shouldPass, "This test failed randomly to simulate flakiness");
  }

  @Test
  public void testTimingDependent() {
    // This test depends on system timing and might be flaky
    long startTime = System.currentTimeMillis();

    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    // This assertion might fail depending on system load
    assertTrue(duration < 100, "Test took too long: " + duration + "ms");
  }
}
