package org.camunda.optimize.service.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Askar Akhmerov
 */
public class NamedThreadFactory implements ThreadFactory {
  private final ThreadGroup group;
  private final AtomicInteger threadNumber = new AtomicInteger(1);
  private final String namePrefix;

  public NamedThreadFactory(String poolName) {
    SecurityManager s = System.getSecurityManager();
    group = (s != null) ? s.getThreadGroup() :
      Thread.currentThread().getThreadGroup();
    namePrefix = poolName +
      "-thread-";
  }

  public Thread newThread(Runnable r) {
    Thread t = new Thread(group, r,
      namePrefix + threadNumber.getAndIncrement(),
      0);
    if (t.isDaemon()) {
      t.setDaemon(false);
    }
    if (t.getPriority() != Thread.NORM_PRIORITY) {
      t.setPriority(Thread.NORM_PRIORITY);
    }
    return t;
  }
}
